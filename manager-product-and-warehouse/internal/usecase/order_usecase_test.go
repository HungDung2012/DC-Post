package usecase

import (
	"context"
	"errors"
	"slices"
	"sync"
	"testing"

	"dc-post/manager-product-and-warehouse/internal/domain"
	"dc-post/manager-product-and-warehouse/internal/dto"
	"dc-post/manager-product-and-warehouse/pkg/apperror"
)

type uc01TxKey struct{}
type uc01Tx struct{ mu sync.Mutex }

func (tx *uc01Tx) WithTransaction(ctx context.Context, fn func(context.Context) error) error {
	tx.mu.Lock()
	defer tx.mu.Unlock()
	return fn(context.WithValue(ctx, uc01TxKey{}, true))
}

type uc01Orders struct {
	OrderStore
	order   domain.Order
	items   []domain.OrderItem
	history []domain.OrderStatusHistory
}

var uc01OrderCodes = []string{"", "PENDING", "CONFIRMED", "PROCESSING", "SHIPPING", "DELIVERED", "CANCELLED", "RETURNED"}

func (r *uc01Orders) Create(_ context.Context, o *domain.Order) error {
	o.ID = 42
	r.order = *o
	return nil
}
func (r *uc01Orders) GetByID(_ context.Context, _ int) (*domain.Order, error) {
	o := r.order
	return &o, nil
}
func (r *uc01Orders) GetByIDForUpdate(ctx context.Context, id int) (*domain.Order, error) {
	if ctx.Value(uc01TxKey{}) != true {
		panic("order lock outside transaction")
	}
	return r.GetByID(ctx, id)
}
func (r *uc01Orders) GetStatusByID(_ context.Context, id int) (*domain.OrderStatus, error) {
	return &domain.OrderStatus{ID: id, Code: uc01OrderCodes[id]}, nil
}
func (r *uc01Orders) GetStatusByCode(ctx context.Context, code string) (*domain.OrderStatus, error) {
	return r.GetStatusByID(ctx, slices.Index(uc01OrderCodes, code))
}
func (r *uc01Orders) UpdateStatus(_ context.Context, _, id int) error {
	r.order.StatusID = id
	return nil
}
func (r *uc01Orders) CreateItems(_ context.Context, items []domain.OrderItem) error {
	for i := range items {
		items[i].ID = i + 1
	}
	r.items = items
	return nil
}
func (r *uc01Orders) ListItems(context.Context, int) ([]domain.OrderItem, error) { return r.items, nil }
func (r *uc01Orders) CreateStatusHistory(_ context.Context, h *domain.OrderStatusHistory) error {
	r.history = append(r.history, *h)
	return nil
}

type uc01Payments struct {
	PaymentStore
	payment domain.Payment
	refunds []domain.Refund
}

func (r *uc01Payments) CreatePayment(_ context.Context, p *domain.Payment) error {
	p.ID = 51
	r.payment = *p
	return nil
}
func (r *uc01Payments) GetPaymentByOrderID(context.Context, int) (*domain.Payment, error) {
	p := r.payment
	return &p, nil
}
func (r *uc01Payments) UpdatePayment(_ context.Context, p *domain.Payment) error {
	r.payment = *p
	return nil
}
func (r *uc01Payments) CreateRefund(_ context.Context, p *domain.Refund) error {
	p.ID = len(r.refunds) + 1
	r.refunds = append(r.refunds, *p)
	return nil
}

type uc01Stock struct {
	domain.WarehouseRepository
	rows         map[[2]int]*domain.Inventory
	locked       []int
	transactions []domain.InventoryTransaction
	assigned     bool
}

func (r *uc01Stock) GetWarehouseByID(_ context.Context, id int) (*domain.Warehouse, error) {
	return &domain.Warehouse{ID: id}, nil
}
func (r *uc01Stock) HasStaff(context.Context, int, int) (bool, error) { return r.assigned, nil }
func (r *uc01Stock) EnsureInventory(_ context.Context, warehouse, id int) error {
	key := [2]int{warehouse, id}
	if r.rows[key] == nil {
		r.rows[key] = &domain.Inventory{WarehouseID: warehouse, ProductID: id}
	}
	return nil
}
func (r *uc01Stock) LockInventoryForUpdate(ctx context.Context, warehouse int, ids []int) (map[int]*domain.Inventory, error) {
	if ctx.Value(uc01TxKey{}) != true {
		panic("inventory lock outside transaction")
	}
	r.locked = append(r.locked, ids...)
	rows := make(map[int]*domain.Inventory)
	for _, id := range ids {
		if row := r.rows[[2]int{warehouse, id}]; row != nil {
			copy := *row
			rows[id] = &copy
		}
	}
	return rows, nil
}
func (r *uc01Stock) UpdateInventory(_ context.Context, i *domain.Inventory) error {
	r.rows[[2]int{i.WarehouseID, i.ProductID}] = i
	return nil
}
func (r *uc01Stock) CreateInventoryTransaction(_ context.Context, tr *domain.InventoryTransaction) error {
	r.transactions = append(r.transactions, *tr)
	return nil
}

type uc01Products struct{ domain.ProductRepository }

func (uc01Products) GetByID(_ context.Context, id int) (*domain.Product, error) {
	return &domain.Product{ID: id, Name: "Snapshot", UnitPrice: 125}, nil
}

var uc01Admin = Actor{Role: RoleAdmin, UserID: 9}
var uc01Staff = Actor{Role: RoleStaff, UserID: 8}

func setupUC01() (*uc01Orders, *uc01Payments, *uc01Stock, *uc01Shipments, *uc01Tx) {
	orders := &uc01Orders{order: domain.Order{ID: 42, CustomerID: 7, WarehouseID: 3, StatusID: 2, TotalAmount: 625, PaymentMethod: "COD"},
		items: []domain.OrderItem{{ID: 1, OrderID: 42, ProductID: 2, Quantity: 3}, {ID: 2, OrderID: 42, ProductID: 1, Quantity: 2}}}
	payments := &uc01Payments{payment: domain.Payment{ID: 51, OrderID: 42, Amount: 625, PaymentMethod: "COD", PaymentStatus: "PENDING"}}
	stock := &uc01Stock{rows: map[[2]int]*domain.Inventory{
		{3, 1}: {WarehouseID: 3, ProductID: 1, Quantity: 10, ReservedQuantity: 2},
		{3, 2}: {WarehouseID: 3, ProductID: 2, Quantity: 10, ReservedQuantity: 3},
	}}
	return orders, payments, stock, &uc01Shipments{}, &uc01Tx{}
}

func TestCreateOrder_ReservesStockAndCreatesPaymentAndHistory(t *testing.T) {
	orders, payments, stock, shipments, tx := setupUC01()
	uc := NewOrderUsecase(orders, payments, stock, uc01Products{}, shipments, tx)
	_, err := uc.CreateOrder(context.Background(), Actor{Role: "CUSTOMER", UserID: 7}, dto.CreateOrderReq{
		CustomID: 99, WarehouseID: 3, PaymentMethod: "COD", Items: []dto.OrderItemReq{{ProductID: 2, Quantity: 3}, {ProductID: 1, Quantity: 2}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if !slices.Equal(stock.locked, []int{1, 2}) || stock.rows[[2]int{3, 1}].ReservedQuantity != 4 || stock.rows[[2]int{3, 2}].ReservedQuantity != 6 {
		t.Fatalf("wrong inventory reservation: locks=%v rows=%+v", stock.locked, stock.rows)
	}
	if len(stock.transactions) != 2 || stock.transactions[0].TransactionType != "RESERVE" || stock.transactions[1].TransactionType != "RESERVE" {
		t.Fatalf("wrong transactions: %+v", stock.transactions)
	}
	if orders.order.WarehouseID != 3 || orders.order.CustomerID != 7 || orders.order.TotalAmount != 625 ||
		len(orders.items) != 2 || orders.items[0].ItemName != "Snapshot" || orders.items[0].UnitPrice != 125 {
		t.Fatalf("wrong order or snapshots: %+v %+v", orders.order, orders.items)
	}
	if len(orders.history) != 1 || orders.history[0].OldStatusID != nil || orders.history[0].NewStatusID != 1 ||
		payments.payment.PaymentStatus != "PENDING" || payments.payment.Amount != 625 {
		t.Fatalf("wrong history or payment: %+v %+v", orders.history, payments.payment)
	}
}
func TestCreateOrder_FailsOnInsufficientAvailableStock(t *testing.T) {
	orders, payments, stock, shipments, tx := setupUC01()
	uc := NewOrderUsecase(orders, payments, stock, uc01Products{}, shipments, tx)
	_, err := uc.CreateOrder(context.Background(), uc01Admin, dto.CreateOrderReq{
		CustomID: 7, WarehouseID: 3, PaymentMethod: "COD", Items: []dto.OrderItemReq{{ProductID: 2, Quantity: 4}, {ProductID: 2, Quantity: 4}},
	})
	var appErr *apperror.AppError
	if !errors.As(err, &appErr) || appErr.Code != "ERR_INSUFFICIENT_STOCK" || len(stock.transactions) != 0 {
		t.Fatalf("err=%v transactions=%+v", err, stock.transactions)
	}
}
func TestCreateOrder_ConcurrentReservationsCannotOversell(t *testing.T) {
	orders, payments, stock, shipments, tx := setupUC01()
	uc := NewOrderUsecase(orders, payments, stock, uc01Products{}, shipments, tx)
	req := dto.CreateOrderReq{CustomID: 7, WarehouseID: 3, PaymentMethod: "COD", Items: []dto.OrderItemReq{{ProductID: 1, Quantity: 5}}}
	outcomes := make(chan error, 2)
	start := make(chan struct{})
	for i := 0; i < 2; i++ {
		go func() { <-start; _, err := uc.CreateOrder(context.Background(), uc01Admin, req); outcomes <- err }()
	}
	close(start)
	success, insufficient := 0, 0
	for i := 0; i < 2; i++ {
		err := <-outcomes
		var appErr *apperror.AppError
		if err == nil {
			success++
		} else if errors.As(err, &appErr) && appErr.Code == "ERR_INSUFFICIENT_STOCK" {
			insufficient++
		} else {
			t.Fatal(err)
		}
	}
	if success != 1 || insufficient != 1 || stock.rows[[2]int{3, 1}].ReservedQuantity != 7 {
		t.Fatalf("success=%d insufficient=%d stock=%+v", success, insufficient, stock.rows[[2]int{3, 1}])
	}
}
func TestUpdateOrderStatus_RejectsShippingDeliveredReturnedCancelled(t *testing.T) {
	orders, payments, stock, shipments, tx := setupUC01()
	uc := NewOrderUsecase(orders, payments, stock, uc01Products{}, shipments, tx)
	for _, code := range []string{"SHIPPING", "DELIVERED", "RETURNED", "CANCELLED"} {
		_, err := uc.UpdateOrderStatus(context.Background(), uc01Admin, 42, dto.UpdateOrderStatusReq{NewStatusCode: code})
		if !errors.Is(err, apperror.ErrInvalidStatusTransition) {
			t.Fatalf("%s: %v", code, err)
		}
	}
	req := dto.UpdateOrderStatusReq{NewStatusCode: "PROCESSING"}
	if _, err := uc.UpdateOrderStatus(context.Background(), uc01Staff, 42, req); !errors.Is(err, apperror.ErrWarehouseAccessDenied) {
		t.Fatalf("unassigned staff: %v", err)
	}
	if len(orders.history) != 0 {
		t.Fatal("rejected transitions wrote history")
	}
	stock.assigned = true
	orders.order.StatusID = 1
	if _, err := uc.UpdateOrderStatus(context.Background(), uc01Staff, 42, dto.UpdateOrderStatusReq{NewStatusCode: "CONFIRMED"}); err != nil {
		t.Fatal(err)
	}
	if _, err := uc.UpdateOrderStatus(context.Background(), uc01Staff, 42, req); err != nil {
		t.Fatal(err)
	}
	if orders.order.StatusID != 3 || len(orders.history) != 2 {
		t.Fatalf("wrong lifecycle: %+v", orders.history)
	}
}
func TestCancelOrder_ReleasesReservedStockAndCreatesRefundWhenPaid(t *testing.T) {
	orders, payments, stock, shipments, tx := setupUC01()
	payments.payment.PaymentStatus = "PAID"
	uc := NewOrderUsecase(orders, payments, stock, uc01Products{}, shipments, tx)
	if _, err := uc.CancelOrder(context.Background(), uc01Admin, 42); err != nil {
		t.Fatal(err)
	}
	if orders.order.StatusID != 6 || len(orders.history) != 1 ||
		stock.rows[[2]int{3, 1}].ReservedQuantity != 0 || stock.rows[[2]int{3, 2}].ReservedQuantity != 0 ||
		stock.rows[[2]int{3, 1}].Quantity != 10 || len(stock.transactions) != 2 || stock.transactions[0].TransactionType != "RELEASE" ||
		len(payments.refunds) != 1 || payments.refunds[0].RefundStatus != "PENDING" || payments.refunds[0].RefundAmount != 625 {
		t.Fatalf("wrong cancellation effects: history=%+v stock=%+v refunds=%+v", orders.history, stock.transactions, payments.refunds)
	}
	if _, err := uc.CancelOrder(context.Background(), uc01Admin, 42); !errors.Is(err, apperror.ErrInvalidStatusTransition) ||
		len(stock.transactions) != 2 || len(payments.refunds) != 1 {
		t.Fatalf("duplicate cancel: %v", err)
	}
}
func TestCancelOrder_RejectsWhenAlreadyShippingOrCancelled(t *testing.T) {
	orders, payments, stock, shipments, tx := setupUC01()
	uc := NewOrderUsecase(orders, payments, stock, uc01Products{}, shipments, tx)
	for _, id := range []int{3, 4, 5, 6, 7} {
		orders.order.StatusID = id
		_, err := uc.CancelOrder(context.Background(), uc01Admin, 42)
		if !errors.Is(err, apperror.ErrInvalidStatusTransition) {
			t.Fatalf("%s: %v", uc01OrderCodes[id], err)
		}
	}
	if len(stock.transactions) != 0 || len(payments.refunds) != 0 {
		t.Fatal("rejected cancellation changed stock or refunds")
	}
}

func (r *uc01Stock) StaffUserExists(context.Context, int) (bool, error) { return true, nil }
func (r *uc01Payments) GetPaymentByID(ctx context.Context, id int) (*domain.Payment, error) {
	return r.GetPaymentByOrderID(ctx, id)
}
func (r *uc01Payments) GetRefundByID(_ context.Context, id int) (*domain.Refund, error) {
	refund := r.refunds[id-1]
	return &refund, nil
}
func (r *uc01Payments) UpdateRefund(_ context.Context, refund *domain.Refund) error {
	r.refunds[refund.ID-1] = *refund
	return nil
}
func TestOrderPaymentConfirmationAndRefundCompletion(t *testing.T) {
	orders, payments, stock, shipments, tx := setupUC01()
	uc := NewOrderUsecase(orders, payments, stock, uc01Products{}, shipments, tx)
	ctx := context.Background()
	req := dto.ConfirmPaymentReq{TransactionCode: "BANK-123"}
	paid, err := uc.ConfirmPayment(ctx, uc01Admin, 42, req)
	if err != nil || paid.PaymentStatus != "PAID" || paid.PaidAt == nil || paid.TransactionCode == nil || *paid.TransactionCode != "BANK-123" {
		t.Fatalf("payment confirmation: %+v, %v", paid, err)
	}
	repeated, err := uc.ConfirmPayment(ctx, uc01Admin, 42, req)
	if err != nil || repeated.PaidAt == nil || !repeated.PaidAt.Equal(*paid.PaidAt) {
		t.Fatalf("payment retry: %+v, %v", repeated, err)
	}
	cancelled, err := uc.CancelOrder(ctx, uc01Admin, 42)
	if err != nil || cancelled.Refund == nil {
		t.Fatalf("cancel: %+v, %v", cancelled, err)
	}
	refund, err := uc.UpdateRefundStatus(ctx, uc01Admin, cancelled.Refund.RefundID, dto.UpdateRefundStatusReq{RefundStatus: "COMPLETED"})
	if err != nil || refund.RefundStatus != "COMPLETED" || payments.payment.PaymentStatus != "REFUNDED" {
		t.Fatalf("refund completion: %+v, payment=%+v, %v", refund, payments.payment, err)
	}
}
func TestOrderPaymentConfirmationClearsOutstandingShipmentCOD(t *testing.T) {
	orders, payments, stock, shipments, tx := setupUC01()
	orderUC := NewOrderUsecase(orders, payments, stock, uc01Products{}, shipments, tx)
	shipmentUC := NewShipmentUsecase(shipments, orders, payments, stock, tx)
	ctx := context.Background()
	if _, err := shipmentUC.CreateShipment(ctx, uc01Admin, dto.CreateShipmentReq{OrderID: 42}); err != nil {
		t.Fatal(err)
	}
	if _, err := orderUC.ConfirmPayment(ctx, uc01Admin, 42, dto.ConfirmPaymentReq{TransactionCode: "CASH-456"}); err != nil {
		t.Fatal(err)
	}
	if shipments.shipment.CODAmount != 0 {
		t.Fatalf("shipment still requests collection of %d", shipments.shipment.CODAmount)
	}
}

func (r *uc01Orders) ListStatusHistory(ctx context.Context, _ int) ([]domain.OrderStatusHistory, error) {
	if ctx.Value(uc01TxKey{}) != true {
		return nil, errors.New("order detail history read outside transaction")
	}
	return r.history, nil
}
func (r *uc01Payments) ListRefundsByPaymentID(context.Context, int) ([]domain.Refund, error) {
	return r.refunds, nil
}
func TestGetOrderDetailReadsLinkedRecordsInOneTransaction(t *testing.T) {
	orders, payments, stock, shipments, tx := setupUC01()
	uc := NewOrderUsecase(orders, payments, stock, uc01Products{}, shipments, tx)
	detail, err := uc.GetOrderDetail(context.Background(), Actor{Role: RoleCustomer, UserID: 7}, 42)
	if err != nil || len(detail.Items) != 2 || detail.Payment.PaymentID != 51 {
		t.Fatalf("detail=%+v err=%v", detail, err)
	}
}
