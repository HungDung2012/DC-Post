package usecase

import (
	"context"
	"errors"
	"strings"
	"time"

	"dc-post/manager-product-and-warehouse/internal/domain"
	"dc-post/manager-product-and-warehouse/internal/dto"
	"dc-post/manager-product-and-warehouse/pkg/apperror"
	"dc-post/manager-product-and-warehouse/pkg/codegen"
)

type ShipmentStore interface {
	domain.ShipmentRepository
	GetByID(ctx context.Context, id int) (*domain.Shipment, error)
}

type ShipmentUsecase struct {
	shipments  ShipmentStore
	orders     domain.OrderRepository
	payments   domain.PaymentRepository
	warehouses OrderWarehouseStore
	tx         domain.TxManager
}

func NewShipmentUsecase(shipments ShipmentStore, orders domain.OrderRepository, payments domain.PaymentRepository, warehouses OrderWarehouseStore, tx domain.TxManager) *ShipmentUsecase {
	return &ShipmentUsecase{shipments: shipments, orders: orders, payments: payments, warehouses: warehouses, tx: tx}
}

func (uc *ShipmentUsecase) CreateShipment(ctx context.Context, actor Actor, req dto.CreateShipmentReq) (dto.ShipmentResponse, error) {
	var result dto.ShipmentResponse
	if err := requireWarehouseRole(actor); err != nil {
		return result, err
	}
	if !validID(req.OrderID) || (req.ShipperID != nil && !validID(*req.ShipperID)) {
		return result, apperror.ErrInvalidRequest
	}
	err := uc.tx.WithTransaction(ctx, func(ctx context.Context) error {
		// Serialize shipment creation with status updates and cancellation.
		order, err := uc.orders.GetByIDForUpdate(ctx, req.OrderID)
		if err != nil {
			return err
		}
		if err := checkStaffWarehouse(ctx, uc.warehouses, actor, order.WarehouseID); err != nil {
			return err
		}
		current, err := uc.orders.GetStatusByID(ctx, order.StatusID)
		if err != nil {
			return err
		}
		if current.Code != domain.StatusConfirmed && current.Code != domain.StatusProcessing {
			return apperror.ErrInvalidStatusTransition
		}
		if _, err := uc.shipments.GetByOrderID(ctx, order.ID); err == nil {
			return apperror.ErrInvalidStatusTransition
		} else if !errors.Is(err, apperror.ErrResourceNotFound) {
			return err
		}

		if _, err := uc.warehouses.GetWarehouseByID(ctx, order.WarehouseID); err != nil {
			return err
		}
		if req.ShipperID != nil {
			if err := checkUserReference(ctx, uc.warehouses, *req.ShipperID); err != nil {
				return err
			}
		}
		items, err := uc.orders.ListItems(ctx, order.ID)
		if err != nil {
			return err
		}
		quantities, err := orderItemQuantities(items)
		if err != nil {
			return err
		}
		status, err := uc.shipments.GetStatusByCode(ctx, domain.ShipmentStatusCreated)
		if err != nil {
			return err
		}
		shipment := domain.Shipment{OrderID: order.ID, TrackingCode: codegen.NewTrackingCode(time.Now().UTC()),
			ShipperID: req.ShipperID, StatusID: status.ID, CurrentWarehouseID: &order.WarehouseID}
		if order.PaymentMethod == domain.PaymentMethodCOD {
			payment, err := uc.payments.GetPaymentByOrderID(ctx, order.ID)
			if err != nil {
				return err
			}
			if payment.PaymentStatus == domain.PaymentStatusPending || payment.PaymentStatus == domain.PaymentStatusFailed {
				shipment.CODAmount = order.TotalAmount
			}
		}
		if err := uc.shipments.Create(ctx, &shipment); err != nil {
			return err
		}
		packed := make([]domain.ShipmentItem, 0, len(items))
		for _, item := range items {
			packed = append(packed, domain.ShipmentItem{ShipmentID: shipment.ID, OrderItemID: item.ID, Quantity: item.Quantity})
		}
		if err := uc.shipments.CreateItems(ctx, packed); err != nil {
			return err
		}
		if err := moveOrderStock(ctx, uc.warehouses, order.WarehouseID, actor.UserID, quantities, domain.TxTypeExport, &shipment.ID); err != nil {
			return err
		}
		if _, err := transitionOrder(ctx, uc.orders, *order, domain.StatusShipping, actor.UserID); err != nil {
			return err
		}
		event := domain.ShipmentTrackingEvent{ShipmentID: shipment.ID, StatusID: status.ID, WarehouseID: shipment.CurrentWarehouseID, ScannedBy: actor.UserID}
		if err := uc.shipments.CreateTrackingEvent(ctx, &event); err != nil {
			return err
		}
		result = shipmentResponse(shipment, *status)
		return nil
	})
	if err != nil {
		return dto.ShipmentResponse{}, err
	}
	return result, nil
}

func (uc *ShipmentUsecase) ScanTrackingEvent(ctx context.Context, actor Actor, id int, req dto.ScanTrackingEventReq) (dto.TrackingEventResponse, error) {
	var result dto.TrackingEventResponse
	if err := requireWarehouseRole(actor); err != nil {
		return result, err
	}
	if !validID(id) || (req.WarehouseID != nil && !validID(*req.WarehouseID)) ||
		(req.FromWarehouseID != nil && !validID(*req.FromWarehouseID)) {
		return result, apperror.ErrInvalidRequest
	}
	switch req.StatusCode {
	case domain.ShipmentStatusReturned:
		if req.WarehouseID == nil {
			return result, apperror.ErrInvalidRequest
		}
	case domain.ShipmentStatusInTransit, domain.ShipmentStatusOutForDelivery, domain.ShipmentStatusDelivered:
	default:
		return result, apperror.ErrInvalidStatusTransition
	}
	err := uc.tx.WithTransaction(ctx, func(ctx context.Context) error {
		// Find the immutable order ID, then take the order lock first so
		// payment confirmation and tracking scans acquire locks in the same order.
		shipment, err := uc.shipments.GetByID(ctx, id)
		if err != nil {
			return err
		}
		order, err := uc.orders.GetByIDForUpdate(ctx, shipment.OrderID)
		if err != nil {
			return err
		}
		shipment, err = uc.shipments.GetByIDForUpdate(ctx, id)
		if err != nil {
			return err
		}

		// Scans belong to the warehouse receiving them. If no destination is
		// supplied, authorize at the last known warehouse or the order's origin.
		scanWarehouse := order.WarehouseID
		if shipment.CurrentWarehouseID != nil {
			scanWarehouse = *shipment.CurrentWarehouseID
		}
		if req.WarehouseID != nil {
			scanWarehouse = *req.WarehouseID
		}
		if err := checkStaffWarehouse(ctx, uc.warehouses, actor, scanWarehouse); err != nil {
			return err
		}
		if _, err := uc.warehouses.GetWarehouseByID(ctx, scanWarehouse); err != nil {
			return err
		}
		if req.FromWarehouseID != nil && *req.FromWarehouseID != scanWarehouse {
			if _, err := uc.warehouses.GetWarehouseByID(ctx, *req.FromWarehouseID); err != nil {
				return err
			}
		}

		current, err := uc.shipments.GetStatusByID(ctx, shipment.StatusID)
		if err != nil {
			return err
		}
		orderStatus, err := uc.orders.GetStatusByID(ctx, order.StatusID)
		if err != nil {
			return err
		}
		if current.Code == domain.ShipmentStatusDelivered || current.Code == domain.ShipmentStatusReturned || orderStatus.Code != domain.StatusShipping {
			return apperror.ErrInvalidStatusTransition
		}
		status, err := uc.shipments.GetStatusByCode(ctx, req.StatusCode)
		if err != nil {
			return err
		}
		event := domain.ShipmentTrackingEvent{ShipmentID: id, StatusID: status.ID, WarehouseID: req.WarehouseID, FromWarehouseID: req.FromWarehouseID, ScannedBy: actor.UserID}
		if err := uc.shipments.CreateTrackingEvent(ctx, &event); err != nil {
			return err
		}
		shipment.StatusID, shipment.CurrentWarehouseID = status.ID, req.WarehouseID
		switch status.Code {
		case domain.ShipmentStatusDelivered:
			shipment.DeliveredAt = &event.ScannedAt
			if _, err := transitionOrder(ctx, uc.orders, *order, domain.StatusDelivered, actor.UserID); err != nil {
				return err
			}
			if order.PaymentMethod == domain.PaymentMethodCOD {
				if err := uc.markCODPaid(ctx, order.ID, event.ScannedAt); err != nil {
					return err
				}
			}
		case domain.ShipmentStatusReturned:
			if err := uc.restockShipment(ctx, *shipment, *req.WarehouseID, actor.UserID); err != nil {
				return err
			}
			if _, err := transitionOrder(ctx, uc.orders, *order, domain.StatusReturned, actor.UserID); err != nil {
				return err
			}
		}
		if err := uc.shipments.Update(ctx, shipment); err != nil {
			return err
		}
		result = trackingEventResponse(event, *status)
		return nil
	})
	if err != nil {
		return dto.TrackingEventResponse{}, err
	}
	return result, nil
}

func (uc *ShipmentUsecase) markCODPaid(ctx context.Context, orderID int, paidAt time.Time) error {
	payment, err := uc.payments.GetPaymentByOrderID(ctx, orderID)
	if err != nil {
		return err
	}
	switch payment.PaymentStatus {
	case domain.PaymentStatusPaid:
		return nil
	case domain.PaymentStatusPending, domain.PaymentStatusFailed:
		payment.PaymentStatus, payment.PaidAt = domain.PaymentStatusPaid, &paidAt
		return uc.payments.UpdatePayment(ctx, payment)
	default:
		return apperror.ErrInvalidStatusTransition
	}
}

func (uc *ShipmentUsecase) restockShipment(ctx context.Context, s domain.Shipment, warehouseID, userID int) error {
	ordered, err := uc.orders.ListItems(ctx, s.OrderID)
	if err != nil {
		return err
	}
	packed, err := uc.shipments.ListItems(ctx, s.ID)
	if err != nil {
		return err
	}
	products := make(map[int]int, len(ordered))
	for _, item := range ordered {
		products[item.ID] = item.ProductID
	}
	items := make([]domain.OrderItem, 0, len(packed))
	for _, item := range packed {
		productID, ok := products[item.OrderItemID]
		if !ok {
			return apperror.ErrResourceNotFound
		}
		items = append(items, domain.OrderItem{ProductID: productID, Quantity: item.Quantity})
	}
	quantities, err := orderItemQuantities(items)
	if err != nil {
		return err
	}
	return moveOrderStock(ctx, uc.warehouses, warehouseID, userID, quantities, domain.TxTypeReturn, &s.ID)
}

func (uc *ShipmentUsecase) GetShipmentByTrackingCode(ctx context.Context, actor Actor, code string) (dto.ShipmentTrackingResponse, error) {
	var result dto.ShipmentTrackingResponse
	if err := requireOrderRole(actor); err != nil {
		return result, err
	}
	code = strings.TrimSpace(code)
	if !validCatalogText(code) {
		return result, apperror.ErrInvalidRequest
	}
	shipment, err := uc.shipments.GetByTrackingCode(ctx, code)
	if err != nil {
		return result, err
	}
	order, err := uc.orders.GetByID(ctx, shipment.OrderID)
	if err != nil {
		return result, err
	}
	if actor.Role == RoleCustomer && order.CustomerID != actor.UserID {
		return result, apperror.ErrResourceNotFound
	}
	warehouseID := order.WarehouseID
	if shipment.CurrentWarehouseID != nil {
		warehouseID = *shipment.CurrentWarehouseID
	}
	if err := checkStaffWarehouse(ctx, uc.warehouses, actor, warehouseID); err != nil {
		return result, err
	}
	status, err := uc.shipments.GetStatusByID(ctx, shipment.StatusID)
	if err != nil {
		return result, err
	}
	result = dto.ShipmentTrackingResponse{ShipmentID: shipment.ID, TrackingCode: shipment.TrackingCode, OrderID: order.ID, CurrentStatus: status.Code, DeliveredAt: shipment.DeliveredAt}
	if shipment.CurrentWarehouseID != nil {
		warehouse, err := uc.warehouses.GetWarehouseByID(ctx, *shipment.CurrentWarehouseID)
		if err != nil {
			return dto.ShipmentTrackingResponse{}, err
		}
		summary := warehouseSummary(*warehouse)
		result.CurrentWarehouse = &summary
	}
	events, err := uc.shipments.ListTrackingEvents(ctx, shipment.ID)
	if err != nil {
		return dto.ShipmentTrackingResponse{}, err
	}
	result.Events = make([]dto.TrackingEventResponse, 0, len(events))
	statuses := map[int]*domain.ShipmentStatus{status.ID: status}
	for _, event := range events {
		status := statuses[event.StatusID]
		if status == nil {
			status, err = uc.shipments.GetStatusByID(ctx, event.StatusID)
			if err != nil {
				return dto.ShipmentTrackingResponse{}, err
			}
			statuses[event.StatusID] = status
		}
		result.Events = append(result.Events, trackingEventResponse(event, *status))
	}
	return result, nil
}

func shipmentResponse(s domain.Shipment, status domain.ShipmentStatus) dto.ShipmentResponse {
	return dto.ShipmentResponse{ShipmentID: s.ID, OrderID: s.OrderID, TrackingCode: s.TrackingCode, ShipperID: s.ShipperID,
		Status: statusResponse(status.ID, status.Code, status.Name), CurrentWarehouseID: s.CurrentWarehouseID, CODAmount: float64(s.CODAmount) / 100, DeliveredAt: s.DeliveredAt}
}
func trackingEventResponse(e domain.ShipmentTrackingEvent, s domain.ShipmentStatus) dto.TrackingEventResponse {
	return dto.TrackingEventResponse{EventID: e.ID, StatusCode: s.Code, StatusName: s.Name, FromWarehouseID: e.FromWarehouseID,
		WarehouseID: e.WarehouseID, ScannedBy: e.ScannedBy, ScannedAt: e.ScannedAt}
}
