package usecase

import (
	"context"
	"errors"
	"testing"
	"time"

	"dc-post/manager-product-and-warehouse/internal/domain"
	"dc-post/manager-product-and-warehouse/internal/dto"
	"dc-post/manager-product-and-warehouse/pkg/apperror"
)

type uc01Shipments struct {
	domain.ShipmentRepository
	shipment domain.Shipment
	items    []domain.ShipmentItem
	events   []domain.ShipmentTrackingEvent
}

var uc01ShipmentCodes = []string{"", "CREATED", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "RETURNED"}

func (r *uc01Shipments) Create(_ context.Context, s *domain.Shipment) error {
	s.ID = 70
	r.shipment = *s
	return nil
}
func (r *uc01Shipments) GetByOrderID(context.Context, int) (*domain.Shipment, error) {
	if r.shipment.ID == 0 {
		return nil, apperror.ErrResourceNotFound
	}
	s := r.shipment
	return &s, nil
}
func (r *uc01Shipments) GetByIDForUpdate(ctx context.Context, id int) (*domain.Shipment, error) {
	if ctx.Value(uc01TxKey{}) != true {
		panic("shipment lock outside transaction")
	}
	return r.GetByOrderID(ctx, id)
}
func (r *uc01Shipments) Update(_ context.Context, s *domain.Shipment) error {
	r.shipment = *s
	return nil
}
func (r *uc01Shipments) GetStatusByID(_ context.Context, id int) (*domain.ShipmentStatus, error) {
	return &domain.ShipmentStatus{ID: id, Code: uc01ShipmentCodes[id]}, nil
}
func (r *uc01Shipments) GetStatusByCode(ctx context.Context, code string) (*domain.ShipmentStatus, error) {
	for id, c := range uc01ShipmentCodes {
		if c == code {
			return r.GetStatusByID(ctx, id)
		}
	}
	return nil, apperror.ErrResourceNotFound
}
func (r *uc01Shipments) CreateItems(_ context.Context, items []domain.ShipmentItem) error {
	r.items = items
	return nil
}
func (r *uc01Shipments) ListItems(context.Context, int) ([]domain.ShipmentItem, error) {
	return r.items, nil
}
func (r *uc01Shipments) CreateTrackingEvent(_ context.Context, e *domain.ShipmentTrackingEvent) error {
	e.ID = len(r.events) + 1
	if e.ScannedAt.IsZero() {
		e.ScannedAt = time.Now()
	}
	r.events = append(r.events, *e)
	return nil
}
func TestCreateShipment_PacksAllOrderItemsDeductsStockAndTransitionsToShipping(t *testing.T) {
	orders, payments, stock, shipments, tx := setupUC01()
	uc := NewShipmentUsecase(shipments, orders, payments, stock, tx)
	req := dto.CreateShipmentReq{OrderID: 42}
	if _, err := uc.CreateShipment(context.Background(), uc01Staff, req); !errors.Is(err, apperror.ErrWarehouseAccessDenied) {
		t.Fatalf("unassigned staff: %v", err)
	}
	stock.assigned = true
	if _, err := uc.CreateShipment(context.Background(), uc01Staff, req); err != nil {
		t.Fatal(err)
	}
	if len(shipments.items) != 2 || shipments.items[0].OrderItemID != 1 || shipments.items[0].Quantity != 3 ||
		shipments.items[1].OrderItemID != 2 || shipments.items[1].Quantity != 2 ||
		shipments.shipment.CODAmount != 625 || len(shipments.events) != 1 || shipments.events[0].StatusID != 1 ||
		orders.order.StatusID != 4 || len(orders.history) != 1 || orders.history[0].NewStatusID != 4 {
		t.Fatalf("wrong dispatch: shipment=%+v items=%+v events=%+v history=%+v", shipments.shipment, shipments.items, shipments.events, orders.history)
	}
	if stock.rows[[2]int{3, 1}].Quantity != 8 || stock.rows[[2]int{3, 2}].Quantity != 7 ||
		stock.rows[[2]int{3, 1}].ReservedQuantity != 0 || stock.rows[[2]int{3, 2}].ReservedQuantity != 0 ||
		len(stock.transactions) != 2 || stock.transactions[0].TransactionType != "EXPORT" ||
		stock.transactions[0].ShipmentID == nil || *stock.transactions[0].ShipmentID != 70 {
		t.Fatalf("wrong export: %+v", stock.transactions)
	}
	if _, err := uc.CreateShipment(context.Background(), uc01Staff, req); err == nil || len(stock.transactions) != 2 {
		t.Fatalf("duplicate dispatch: %v", err)
	}
}
func TestScanTrackingEvent_DeliveredMarksOrderDeliveredAndCODPaid(t *testing.T) {
	orders, payments, stock, shipments, tx := setupUC01()
	uc := NewShipmentUsecase(shipments, orders, payments, stock, tx)
	if _, err := uc.CreateShipment(context.Background(), uc01Admin, dto.CreateShipmentReq{OrderID: 42}); err != nil {
		t.Fatal(err)
	}
	warehouse := 5
	req := dto.ScanTrackingEventReq{StatusCode: "DELIVERED", WarehouseID: &warehouse}
	if _, err := uc.ScanTrackingEvent(context.Background(), uc01Admin, 70, req); err != nil {
		t.Fatal(err)
	}
	if orders.order.StatusID != 5 || shipments.shipment.StatusID != 4 || shipments.shipment.DeliveredAt == nil ||
		shipments.shipment.CurrentWarehouseID == nil || *shipments.shipment.CurrentWarehouseID != 5 ||
		payments.payment.PaymentStatus != "PAID" || payments.payment.PaidAt == nil ||
		len(shipments.events) != 2 || len(orders.history) != 2 {
		t.Fatal("delivery effects missing")
	}
	if _, err := uc.ScanTrackingEvent(context.Background(), uc01Admin, 70, req); !errors.Is(err, apperror.ErrInvalidStatusTransition) || len(shipments.events) != 2 {
		t.Fatalf("duplicate delivery: %v", err)
	}
}
func TestScanTrackingEvent_ReturnedRestoresStockAtScanWarehouse(t *testing.T) {
	orders, payments, stock, shipments, tx := setupUC01()
	uc := NewShipmentUsecase(shipments, orders, payments, stock, tx)
	if _, err := uc.CreateShipment(context.Background(), uc01Admin, dto.CreateShipmentReq{OrderID: 42}); err != nil {
		t.Fatal(err)
	}
	warehouse := 5
	req := dto.ScanTrackingEventReq{StatusCode: "RETURNED", WarehouseID: &warehouse}
	if _, err := uc.ScanTrackingEvent(context.Background(), uc01Admin, 70, req); err != nil {
		t.Fatal(err)
	}
	if orders.order.StatusID != 7 || stock.rows[[2]int{3, 1}].Quantity != 8 ||
		stock.rows[[2]int{5, 1}].Quantity != 2 || stock.rows[[2]int{5, 2}].Quantity != 3 ||
		stock.rows[[2]int{5, 1}].ReservedQuantity != 0 || len(stock.transactions) != 4 || stock.transactions[2].TransactionType != "RETURN" ||
		stock.transactions[2].ShipmentID == nil || *stock.transactions[2].ShipmentID != 70 {
		t.Fatalf("wrong return: %+v", stock.transactions)
	}
	if _, err := uc.ScanTrackingEvent(context.Background(), uc01Admin, 70, req); !errors.Is(err, apperror.ErrInvalidStatusTransition) ||
		len(stock.transactions) != 4 {
		t.Fatalf("duplicate return: %v", err)
	}
}

func (r *uc01Shipments) GetByID(ctx context.Context, id int) (*domain.Shipment, error) {
	return r.GetByOrderID(ctx, id)
}
