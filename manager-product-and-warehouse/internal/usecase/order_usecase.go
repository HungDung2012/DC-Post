package usecase

import (
	"context"
	"errors"
	"math"
	"slices"
	"strings"
	"time"

	"dc-post/manager-product-and-warehouse/internal/domain"
	"dc-post/manager-product-and-warehouse/internal/dto"
	"dc-post/manager-product-and-warehouse/pkg/apperror"
	"dc-post/manager-product-and-warehouse/pkg/codegen"
)

const RoleCustomer = "CUSTOMER"
const maxOrderAmount = 999_999_999_999_999 // DECIMAL(15, 2) in hundredths.

type OrderStore interface {
	domain.OrderRepository
	ListStaffOrders(ctx context.Context, userID int, filter domain.OrderFilter) ([]domain.Order, int, error)
}
type PaymentStore interface {
	domain.PaymentRepository
	GetPaymentByID(ctx context.Context, id int) (*domain.Payment, error)
	ListRefundsByPaymentID(ctx context.Context, paymentID int) ([]domain.Refund, error)
}
type OrderWarehouseStore interface {
	domain.WarehouseRepository
	// The warehouse repository's existing lookup checks the shared users table.
	StaffUserExists(ctx context.Context, userID int) (bool, error)
}

type OrderUsecase struct {
	orders     OrderStore
	payments   PaymentStore
	warehouses OrderWarehouseStore
	products   domain.ProductRepository
	shipments  domain.ShipmentRepository
	tx         domain.TxManager
}

func NewOrderUsecase(orders OrderStore, payments PaymentStore, warehouses OrderWarehouseStore, products domain.ProductRepository, shipments domain.ShipmentRepository, tx domain.TxManager) *OrderUsecase {
	return &OrderUsecase{orders: orders, payments: payments, warehouses: warehouses, products: products, shipments: shipments, tx: tx}
}

func (uc *OrderUsecase) CreateOrder(ctx context.Context, actor Actor, req dto.CreateOrderReq) (dto.OrderResponse, error) {
	var result dto.OrderResponse
	if err := requireOrderRole(actor); err != nil {
		return result, err
	}
	customerID := req.CustomID
	if actor.Role == RoleCustomer {
		customerID = actor.UserID
	}
	if !validID(customerID) || !validID(req.WarehouseID) || len(req.Items) == 0 {
		return result, apperror.ErrInvalidRequest
	}
	switch req.PaymentMethod {
	case domain.PaymentMethodCOD, domain.PaymentMethodBankTransfer, domain.PaymentMethodEWallet:
	default:
		return result, apperror.ErrInvalidRequest
	}
	quantities := make(map[int]int, len(req.Items))
	for _, i := range req.Items {
		if !validID(i.ProductID) || i.Quantity <= 0 || i.Quantity > math.MaxInt32-quantities[i.ProductID] {
			return result, apperror.ErrInvalidRequest
		}
		quantities[i.ProductID] += i.Quantity
	}

	err := uc.tx.WithTransaction(ctx, func(ctx context.Context) error {
		if err := checkStaffWarehouse(ctx, uc.warehouses, actor, req.WarehouseID); err != nil {
			return err
		}
		if _, err := uc.warehouses.GetWarehouseByID(ctx, req.WarehouseID); err != nil {
			return err
		}
		if err := checkUserReference(ctx, uc.warehouses, customerID); err != nil {
			return err
		}
		pending, err := uc.orders.GetStatusByCode(ctx, domain.StatusPending)
		if err != nil {
			return err
		}

		ids := sortedProductIDs(quantities)
		products := make(map[int]*domain.Product, len(ids))
		for _, id := range ids {
			product, err := uc.products.GetByID(ctx, id)
			if err != nil {
				return err
			}
			products[id] = product
		}
		items := make([]domain.OrderItem, 0, len(req.Items))
		order := domain.Order{Code: codegen.NewOrderCode(time.Now().UTC()), CustomerID: customerID, WarehouseID: req.WarehouseID, StatusID: pending.ID, PaymentMethod: req.PaymentMethod}
		for _, line := range req.Items {
			product := products[line.ProductID]
			if product.UnitPrice < 0 || product.UnitPrice > (maxOrderAmount-order.TotalAmount)/int64(line.Quantity) {
				return apperror.ErrInvalidRequest
			}
			order.TotalAmount += product.UnitPrice * int64(line.Quantity)
			items = append(items, domain.OrderItem{ProductID: line.ProductID, ItemName: product.Name, Quantity: line.Quantity, UnitPrice: product.UnitPrice})
		}
		if err := moveOrderStock(ctx, uc.warehouses, req.WarehouseID, actor.UserID, quantities, domain.TxTypeReserve, nil); err != nil {
			return err
		}
		if err := uc.orders.Create(ctx, &order); err != nil {
			return err
		}
		for i := range items {
			items[i].OrderID = order.ID
		}
		if err := uc.orders.CreateItems(ctx, items); err != nil {
			return err
		}
		history := domain.OrderStatusHistory{OrderID: order.ID, NewStatusID: pending.ID, ChangedBy: actor.UserID}
		if err := uc.orders.CreateStatusHistory(ctx, &history); err != nil {
			return err
		}
		payment := domain.Payment{OrderID: order.ID, Amount: order.TotalAmount, PaymentMethod: order.PaymentMethod, PaymentStatus: domain.PaymentStatusPending}
		if err := uc.payments.CreatePayment(ctx, &payment); err != nil {
			return err
		}
		result = orderResponse(order, *pending, items, payment)
		return nil
	})
	if err != nil {
		return dto.OrderResponse{}, err
	}
	return result, nil
}

func (uc *OrderUsecase) ListOrders(ctx context.Context, actor Actor, query dto.OrderListQuery) ([]dto.OrderSummary, int, error) {
	if err := requireOrderRole(actor); err != nil {
		return nil, 0, err
	}
	page, err := listPage(query.Page, query.Limit)
	if err != nil {
		return nil, 0, err
	}
	filter := domain.OrderFilter{Page: page, OrderCode: strings.TrimSpace(query.OrderCode), StatusCode: strings.TrimSpace(query.StatusCode), CustomerID: query.CustomID}
	if actor.Role == RoleCustomer {
		filter.CustomerID = &actor.UserID
	} else if filter.CustomerID != nil && !validID(*filter.CustomerID) {
		return nil, 0, apperror.ErrInvalidRequest
	}
	if query.FromDate != "" {
		from, err := time.Parse(time.DateOnly, query.FromDate)
		if err != nil {
			return nil, 0, apperror.ErrInvalidRequest
		}
		filter.From = &from
	}
	if query.ToDate != "" {
		to, err := time.Parse(time.DateOnly, query.ToDate)
		if err != nil {
			return nil, 0, apperror.ErrInvalidRequest
		}
		before := to.AddDate(0, 0, 1)
		filter.Before = &before
	}
	if filter.From != nil && filter.Before != nil && !filter.From.Before(*filter.Before) {
		return nil, 0, apperror.ErrInvalidRequest
	}
	var orders []domain.Order
	var total int
	if actor.Role == RoleStaff {
		orders, total, err = uc.orders.ListStaffOrders(ctx, actor.UserID, filter)
	} else {
		orders, total, err = uc.orders.List(ctx, filter)
	}
	if err != nil {
		return nil, 0, err
	}
	result := make([]dto.OrderSummary, 0, len(orders))
	statuses := make(map[int]*domain.OrderStatus)
	for _, o := range orders {
		status := statuses[o.StatusID]
		if status == nil {
			status, err = uc.orders.GetStatusByID(ctx, o.StatusID)
			if err != nil {
				return nil, 0, err
			}
			statuses[o.StatusID] = status
		}
		result = append(result, orderSummary(o, *status))
	}
	return result, total, nil
}

func (uc *OrderUsecase) GetOrderDetail(ctx context.Context, actor Actor, id int) (dto.OrderDetailResponse, error) {
	if err := requireOrderRole(actor); err != nil {
		return dto.OrderDetailResponse{}, err
	}
	if !validID(id) {
		return dto.OrderDetailResponse{}, apperror.ErrInvalidRequest
	}
	var result dto.OrderDetailResponse
	err := uc.tx.WithTransaction(ctx, func(ctx context.Context) error {
		var err error
		result, err = uc.readOrderDetail(ctx, actor, id)
		return err
	})
	if err != nil {
		return dto.OrderDetailResponse{}, err
	}
	return result, nil
}

func (uc *OrderUsecase) readOrderDetail(ctx context.Context, actor Actor, id int) (dto.OrderDetailResponse, error) {
	var result dto.OrderDetailResponse
	// Order mutations use this lock before changing any linked records.
	order, err := uc.accessibleOrder(ctx, actor, id, true)
	if err != nil {
		return result, err
	}
	status, err := uc.orders.GetStatusByID(ctx, order.StatusID)
	if err != nil {
		return result, err
	}
	items, err := uc.orders.ListItems(ctx, id)
	if err != nil {
		return result, err
	}
	payment, err := uc.payments.GetPaymentByOrderID(ctx, id)
	if err != nil {
		return result, err
	}
	refunds, err := uc.payments.ListRefundsByPaymentID(ctx, payment.ID)
	if err != nil {
		return result, err
	}
	shipment, err := uc.shipments.GetByOrderID(ctx, id)
	if err != nil && !errors.Is(err, apperror.ErrResourceNotFound) {
		return result, err
	}
	shipments := make([]dto.ShipmentResponse, 0, 1)
	if shipment != nil {
		status, err := uc.shipments.GetStatusByID(ctx, shipment.StatusID)
		if err != nil {
			return result, err
		}
		shipments = append(shipments, shipmentResponse(*shipment, *status))
	}
	history, err := uc.statusHistory(ctx, id)
	if err != nil {
		return result, err
	}
	result = dto.OrderDetailResponse{OrderResponse: orderResponse(*order, *status, items, *payment), Refunds: make([]dto.RefundResponse, 0, len(refunds)), Shipments: shipments, StatusHistory: history}
	for _, refund := range refunds {
		result.Refunds = append(result.Refunds, refundResponse(refund))
	}
	return result, nil
}

func (uc *OrderUsecase) UpdateOrderStatus(ctx context.Context, actor Actor, id int, req dto.UpdateOrderStatusReq) (dto.OrderStatusChangeResponse, error) {
	var result dto.OrderStatusChangeResponse
	if err := requireWarehouseRole(actor); err != nil {
		return result, err
	}
	err := uc.tx.WithTransaction(ctx, func(ctx context.Context) error {
		order, err := uc.accessibleOrder(ctx, actor, id, true)
		if err != nil {
			return err
		}
		current, err := uc.orders.GetStatusByID(ctx, order.StatusID)
		if err != nil {
			return err
		}
		if !domain.CanUpdateOrderStatus(current.Code, req.NewStatusCode) {
			return apperror.ErrInvalidStatusTransition
		}
		history, err := transitionOrder(ctx, uc.orders, *order, req.NewStatusCode, actor.UserID)
		if err != nil {
			return err
		}
		result = dto.OrderStatusChangeResponse{OrderID: id, OrderCode: order.Code, OldStatusCode: current.Code, NewStatusCode: req.NewStatusCode, ChangedBy: actor.UserID, ChangeAt: history.ChangeAt}
		return nil
	})
	if err != nil {
		return dto.OrderStatusChangeResponse{}, err
	}
	return result, nil
}

func (uc *OrderUsecase) GetOrderStatusHistory(ctx context.Context, actor Actor, id int) ([]dto.OrderStatusHistoryResponse, error) {
	if _, err := uc.accessibleOrder(ctx, actor, id, false); err != nil {
		return nil, err
	}
	return uc.statusHistory(ctx, id)
}

func (uc *OrderUsecase) statusHistory(ctx context.Context, id int) ([]dto.OrderStatusHistoryResponse, error) {
	rows, err := uc.orders.ListStatusHistory(ctx, id)
	if err != nil {
		return nil, err
	}
	statuses := make(map[int]dto.StatusResponse)
	statusByID := func(id int) (dto.StatusResponse, error) {
		if status, ok := statuses[id]; ok {
			return status, nil
		}
		status, err := uc.orders.GetStatusByID(ctx, id)
		if err != nil {
			return dto.StatusResponse{}, err
		}
		response := statusResponse(status.ID, status.Code, status.Name)
		statuses[id] = response
		return response, nil
	}
	result := make([]dto.OrderStatusHistoryResponse, 0, len(rows))
	for _, row := range rows {
		next, err := statusByID(row.NewStatusID)
		if err != nil {
			return nil, err
		}
		entry := dto.OrderStatusHistoryResponse{HistoryID: row.ID, OrderID: row.OrderID, NewStatus: next, ChangedBy: row.ChangedBy, ChangeAt: row.ChangeAt}
		if row.OldStatusID != nil {
			previous, err := statusByID(*row.OldStatusID)
			if err != nil {
				return nil, err
			}
			entry.OldStatus = &previous
		}
		result = append(result, entry)
	}
	return result, nil
}

func (uc *OrderUsecase) CancelOrder(ctx context.Context, actor Actor, id int) (dto.CancelOrderResponse, error) {
	var result dto.CancelOrderResponse
	err := uc.tx.WithTransaction(ctx, func(ctx context.Context) error {
		order, err := uc.accessibleOrder(ctx, actor, id, true)
		if err != nil {
			return err
		}
		current, err := uc.orders.GetStatusByID(ctx, order.StatusID)
		if err != nil {
			return err
		}
		if !domain.CanTransitionOrderStatus(current.Code, domain.StatusCancelled) {
			return apperror.ErrInvalidStatusTransition
		}
		items, err := uc.orders.ListItems(ctx, id)
		if err != nil {
			return err
		}
		quantities, err := orderItemQuantities(items)
		if err != nil {
			return err
		}
		payment, err := uc.payments.GetPaymentByOrderID(ctx, id)
		if err != nil {
			return err
		}
		if err := moveOrderStock(ctx, uc.warehouses, order.WarehouseID, actor.UserID, quantities, domain.TxTypeRelease, nil); err != nil {
			return err
		}
		if _, err := transitionOrder(ctx, uc.orders, *order, domain.StatusCancelled, actor.UserID); err != nil {
			return err
		}
		result = dto.CancelOrderResponse{OrderID: id, StatusCode: domain.StatusCancelled, StockReleased: true}
		if payment.PaymentStatus == domain.PaymentStatusPaid {
			refund := domain.Refund{PaymentID: payment.ID, RefundAmount: payment.Amount, RefundStatus: domain.RefundStatusPending}
			if err := uc.payments.CreateRefund(ctx, &refund); err != nil {
				return err
			}
			response := refundResponse(refund)
			result.Refund = &response
		}
		return nil
	})
	if err != nil {
		return dto.CancelOrderResponse{}, err
	}
	return result, nil
}

func (uc *OrderUsecase) ConfirmPayment(ctx context.Context, actor Actor, id int, req dto.ConfirmPaymentReq) (dto.PaymentResponse, error) {
	if err := requireWarehouseRole(actor); err != nil {
		return dto.PaymentResponse{}, err
	}
	return uc.confirmPayment(ctx, id, req, func(ctx context.Context, order *domain.Order) error {
		return checkStaffWarehouse(ctx, uc.warehouses, actor, order.WarehouseID)
	})
}

// ConfirmWebhookPayment is for adapters that have authenticated a payment
// provider and verified that its notification belongs to this order.
func (uc *OrderUsecase) ConfirmWebhookPayment(ctx context.Context, id int, req dto.ConfirmPaymentReq) (dto.PaymentResponse, error) {
	if strings.TrimSpace(req.TransactionCode) == "" {
		return dto.PaymentResponse{}, apperror.ErrInvalidRequest
	}
	return uc.confirmPayment(ctx, id, req, func(context.Context, *domain.Order) error { return nil })
}

func (uc *OrderUsecase) confirmPayment(ctx context.Context, id int, req dto.ConfirmPaymentReq, authorize func(context.Context, *domain.Order) error) (dto.PaymentResponse, error) {
	var result dto.PaymentResponse
	code := strings.TrimSpace(req.TransactionCode)
	if !validID(id) || (code != "" && !validCatalogText(code)) {
		return result, apperror.ErrInvalidRequest
	}
	err := uc.tx.WithTransaction(ctx, func(ctx context.Context) error {
		order, err := uc.orders.GetByIDForUpdate(ctx, id)
		if err != nil {
			return err
		}
		if err := authorize(ctx, order); err != nil {
			return err
		}
		payment, err := uc.payments.GetPaymentByOrderID(ctx, id)
		if err != nil {
			return err
		}
		if payment.PaymentStatus == domain.PaymentStatusPaid &&
			((payment.TransactionCode == nil && code == "") || (payment.TransactionCode != nil && *payment.TransactionCode == code)) {
			result = paymentResponse(*payment)
			return nil
		}
		status, err := uc.orders.GetStatusByID(ctx, order.StatusID)
		if err != nil {
			return err
		}
		if status.Code == domain.StatusCancelled || status.Code == domain.StatusReturned ||
			(payment.PaymentStatus != domain.PaymentStatusPending && payment.PaymentStatus != domain.PaymentStatusFailed) {
			return apperror.ErrInvalidStatusTransition
		}
		if code != "" {
			payment.TransactionCode = &code
		}
		now := time.Now().UTC()
		payment.PaymentStatus, payment.PaidAt = domain.PaymentStatusPaid, &now
		if err := uc.payments.UpdatePayment(ctx, payment); err != nil {
			return err
		}
		if order.PaymentMethod == domain.PaymentMethodCOD && status.Code == domain.StatusShipping {
			shipment, err := uc.shipments.GetByOrderID(ctx, order.ID)
			if err != nil {
				return err
			}
			shipment.CODAmount = 0
			if err := uc.shipments.Update(ctx, shipment); err != nil {
				return err
			}
		}
		result = paymentResponse(*payment)
		return nil
	})
	if err != nil {
		return dto.PaymentResponse{}, err
	}
	return result, nil
}

func (uc *OrderUsecase) UpdateRefundStatus(ctx context.Context, actor Actor, id int, req dto.UpdateRefundStatusReq) (dto.RefundResponse, error) {
	var result dto.RefundResponse
	if err := requireWarehouseRole(actor); err != nil {
		return result, err
	}
	if !validID(id) {
		return result, apperror.ErrInvalidRequest
	}
	if req.RefundStatus != domain.RefundStatusCompleted && req.RefundStatus != domain.RefundStatusRejected {
		return result, apperror.ErrInvalidStatusTransition
	}
	err := uc.tx.WithTransaction(ctx, func(ctx context.Context) error {
		refund, err := uc.payments.GetRefundByID(ctx, id)
		if err != nil {
			return err
		}
		payment, err := uc.payments.GetPaymentByID(ctx, refund.PaymentID)
		if err != nil {
			return err
		}
		if _, err := uc.accessibleOrder(ctx, actor, payment.OrderID, true); err != nil {
			return err
		}

		// Reread after taking the common order lock used by payment mutations.
		refund, err = uc.payments.GetRefundByID(ctx, id)
		if err != nil {
			return err
		}
		if refund.RefundStatus == req.RefundStatus {
			result = refundResponse(*refund)
			return nil
		}
		if refund.RefundStatus != domain.RefundStatusPending {
			return apperror.ErrInvalidStatusTransition
		}
		payment, err = uc.payments.GetPaymentByID(ctx, refund.PaymentID)
		if err != nil {
			return err
		}
		if payment.PaymentStatus != domain.PaymentStatusPaid {
			return apperror.ErrInvalidStatusTransition
		}
		refund.RefundStatus = req.RefundStatus
		if err := uc.payments.UpdateRefund(ctx, refund); err != nil {
			return err
		}
		if req.RefundStatus == domain.RefundStatusCompleted {
			payment.PaymentStatus = domain.PaymentStatusRefunded
			if err := uc.payments.UpdatePayment(ctx, payment); err != nil {
				return err
			}
		}
		result = refundResponse(*refund)
		return nil
	})
	if err != nil {
		return dto.RefundResponse{}, err
	}
	return result, nil
}

func requireOrderRole(actor Actor) error {
	if actor.Role == RoleCustomer && validID(actor.UserID) {
		return nil
	}
	return requireWarehouseRole(actor)
}

func checkStaffWarehouse(ctx context.Context, warehouses domain.WarehouseRepository, actor Actor, warehouseID int) error {
	if actor.Role != RoleStaff {
		return nil
	}
	assigned, err := warehouses.HasStaff(ctx, warehouseID, actor.UserID)
	if err != nil {
		return err
	}
	if !assigned {
		return apperror.ErrWarehouseAccessDenied
	}
	return nil
}

func checkUserReference(ctx context.Context, warehouses OrderWarehouseStore, id int) error {
	exists, err := warehouses.StaffUserExists(ctx, id)
	if err != nil {
		return err
	}
	if !exists {
		return apperror.ErrResourceNotFound
	}
	return nil
}

func (uc *OrderUsecase) accessibleOrder(ctx context.Context, actor Actor, id int, lock bool) (*domain.Order, error) {
	if err := requireOrderRole(actor); err != nil {
		return nil, err
	}
	if !validID(id) {
		return nil, apperror.ErrInvalidRequest
	}
	read := uc.orders.GetByID
	if lock {
		read = uc.orders.GetByIDForUpdate
	}
	order, err := read(ctx, id)
	if err != nil {
		return nil, err
	}
	if actor.Role == RoleCustomer && order.CustomerID != actor.UserID {
		return nil, apperror.ErrResourceNotFound
	}
	if err := checkStaffWarehouse(ctx, uc.warehouses, actor, order.WarehouseID); err != nil {
		return nil, err
	}
	return order, nil
}

func transitionOrder(ctx context.Context, orders domain.OrderRepository, o domain.Order, next string, userID int) (domain.OrderStatusHistory, error) {
	status, err := orders.GetStatusByCode(ctx, next)
	if err != nil {
		return domain.OrderStatusHistory{}, err
	}
	if err := orders.UpdateStatus(ctx, o.ID, status.ID); err != nil {
		return domain.OrderStatusHistory{}, err
	}
	history := domain.OrderStatusHistory{OrderID: o.ID, OldStatusID: &o.StatusID, NewStatusID: status.ID, ChangedBy: userID}
	err = orders.CreateStatusHistory(ctx, &history)
	return history, err
}

func orderItemQuantities(items []domain.OrderItem) (map[int]int, error) {
	if len(items) == 0 {
		return nil, apperror.ErrInvalidRequest
	}
	quantities := make(map[int]int, len(items))
	for _, item := range items {
		if !validID(item.ProductID) || item.Quantity <= 0 || item.Quantity > math.MaxInt32-quantities[item.ProductID] {
			return nil, apperror.ErrInvalidRequest
		}
		quantities[item.ProductID] += item.Quantity
	}
	return quantities, nil
}

func sortedProductIDs(quantities map[int]int) []int {
	ids := make([]int, 0, len(quantities))
	for id := range quantities {
		ids = append(ids, id)
	}
	slices.Sort(ids)
	return ids
}

func moveOrderStock(ctx context.Context, warehouses domain.WarehouseRepository, warehouseID, userID int, quantities map[int]int, kind string, shipmentID *int) error {
	ids := sortedProductIDs(quantities)
	rows := make(map[int]*domain.Inventory, len(ids))
	if kind == domain.TxTypeReturn {
		// Ensure and lock one row at a time. The insert itself can wait on a
		// concurrent stock change and must follow the same order as row locks.
		for i, id := range ids {
			if err := warehouses.EnsureInventory(ctx, warehouseID, id); err != nil {
				return err
			}
			locked, err := warehouses.LockInventoryForUpdate(ctx, warehouseID, ids[i:i+1])
			if err != nil {
				return err
			}
			rows[id] = locked[id]
		}
	} else {
		var err error
		rows, err = warehouses.LockInventoryForUpdate(ctx, warehouseID, ids)
		if err != nil {
			return err
		}
	}
	changes := make([]domain.InventoryTransaction, 0, len(ids))
	for _, id := range ids {
		row, qty := rows[id], quantities[id]
		if kind == domain.TxTypeReserve {
			available := 0
			if row != nil {
				available = row.AvailableQuantity()
			}
			if available < qty {
				e := apperror.ErrInsufficientStock
				return apperror.New(e.HTTPStatus, e.Code, e.Message, map[string]int{"product_id": id, "requested": qty, "available": available})
			}
		}
		if row == nil {
			return apperror.ErrInsufficientStock
		}
		delta := qty
		switch kind {
		case domain.TxTypeReserve:
			row.ReservedQuantity += qty
		case domain.TxTypeRelease, domain.TxTypeExport:
			if row.ReservedQuantity < qty || row.Quantity < row.ReservedQuantity {
				return apperror.ErrInsufficientStock
			}
			delta = -qty
			row.ReservedQuantity -= qty
			if kind == domain.TxTypeExport {
				row.Quantity -= qty
			}
		case domain.TxTypeReturn:
			if row.Quantity > math.MaxInt32-qty {
				return apperror.ErrInvalidRequest
			}
			row.Quantity += qty
		}
		changes = append(changes, domain.InventoryTransaction{ProductID: id, WarehouseID: warehouseID, ShipmentID: shipmentID, PerformedBy: userID, TransactionType: kind, Quantity: delta})
	}
	for i, id := range ids {
		if err := warehouses.UpdateInventory(ctx, rows[id]); err != nil {
			return err
		}
		if err := warehouses.CreateInventoryTransaction(ctx, &changes[i]); err != nil {
			return err
		}
	}
	return nil
}

func statusResponse(id int, code, name string) dto.StatusResponse {
	return dto.StatusResponse{StatusID: id, StatusCode: code, StatusName: name}
}
func orderSummary(o domain.Order, s domain.OrderStatus) dto.OrderSummary {
	return dto.OrderSummary{OrderID: o.ID, OrderCode: o.Code, CustomID: o.CustomerID, WarehouseID: o.WarehouseID,
		Status: statusResponse(s.ID, s.Code, s.Name), TotalAmount: float64(o.TotalAmount) / 100, PaymentMethod: o.PaymentMethod, CreatedAt: o.CreatedAt}
}
func orderResponse(o domain.Order, s domain.OrderStatus, items []domain.OrderItem, p domain.Payment) dto.OrderResponse {
	result := dto.OrderResponse{OrderSummary: orderSummary(o, s), Items: make([]dto.OrderItemResponse, 0, len(items)), Payment: paymentResponse(p)}
	for _, i := range items {
		result.Items = append(result.Items, dto.OrderItemResponse{OrderItemID: i.ID, ProductID: i.ProductID, ItemName: i.ItemName, Quantity: i.Quantity, UnitPrice: float64(i.UnitPrice) / 100})
	}
	return result
}
func paymentResponse(p domain.Payment) dto.PaymentResponse {
	return dto.PaymentResponse{PaymentID: p.ID, Amount: float64(p.Amount) / 100, PaymentMethod: p.PaymentMethod, PaymentStatus: p.PaymentStatus, TransactionCode: p.TransactionCode, PaidAt: p.PaidAt}
}
func refundResponse(r domain.Refund) dto.RefundResponse {
	return dto.RefundResponse{RefundID: r.ID, PaymentID: r.PaymentID, RefundAmount: float64(r.RefundAmount) / 100, RefundStatus: r.RefundStatus, CreatedAt: r.CreatedAt}
}
