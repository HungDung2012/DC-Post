package domain

import (
	"context"
	"time"
)

// Page describes a repository list window. Paginated repository methods return
// the rows and the total matching count before pagination.
type Page struct {
	Limit  int
	Offset int
}

type ProductFilter struct {
	Page
	Search string
}

type OrderFilter struct {
	Page
	CustomerID *int
	OrderCode  string
	StatusCode string
	From       *time.Time // Inclusive.
	Before     *time.Time // Exclusive.
}

type InventoryFilter struct {
	Page
	Search   string
	LowStock bool
}

type InventoryTransactionFilter struct {
	Page
	WarehouseID     *int
	ProductID       *int
	ShipmentID      *int
	PerformedBy     *int
	TransactionType string
	From            *time.Time // Inclusive.
	Before          *time.Time // Exclusive.
}

type ProductRepository interface {
	Create(ctx context.Context, product *Product) error
	GetByID(ctx context.Context, id int) (*Product, error)
	List(ctx context.Context, filter ProductFilter) ([]Product, int, error)
	Update(ctx context.Context, product *Product) error
}

type WarehouseRepository interface {
	CreateWarehouse(ctx context.Context, warehouse *Warehouse) error
	GetWarehouseByID(ctx context.Context, id int) (*Warehouse, error)
	ListWarehouses(ctx context.Context, page Page) ([]Warehouse, int, error)
	UpdateWarehouse(ctx context.Context, warehouse *Warehouse) error

	AssignStaff(ctx context.Context, staff WarehouseStaff) error
	RemoveStaff(ctx context.Context, staff WarehouseStaff) error
	HasStaff(ctx context.Context, warehouseID, userID int) (bool, error)

	ListInventory(ctx context.Context, warehouseID int, filter InventoryFilter) ([]Inventory, int, error)
	// LockInventoryForUpdate requires a transaction and locks existing rows
	// in ascending product ID order. Missing products are omitted.
	LockInventoryForUpdate(ctx context.Context, warehouseID int, productIDs []int) (map[int]*Inventory, error)
	// EnsureInventory creates an empty row if absent and preserves any existing row.
	EnsureInventory(ctx context.Context, warehouseID, productID int) error
	UpdateInventory(ctx context.Context, inventory *Inventory) error
	CreateInventoryTransaction(ctx context.Context, transaction *InventoryTransaction) error
	ListInventoryTransactions(ctx context.Context, filter InventoryTransactionFilter) ([]InventoryTransaction, int, error)
}

type OrderRepository interface {
	Create(ctx context.Context, order *Order) error
	GetByID(ctx context.Context, id int) (*Order, error)
	GetByIDForUpdate(ctx context.Context, id int) (*Order, error)
	List(ctx context.Context, filter OrderFilter) ([]Order, int, error)
	UpdateStatus(ctx context.Context, orderID, statusID int) error
	GetStatusByID(ctx context.Context, id int) (*OrderStatus, error)
	GetStatusByCode(ctx context.Context, code string) (*OrderStatus, error)

	CreateItems(ctx context.Context, items []OrderItem) error
	ListItems(ctx context.Context, orderID int) ([]OrderItem, error)
	CreateStatusHistory(ctx context.Context, history *OrderStatusHistory) error
	ListStatusHistory(ctx context.Context, orderID int) ([]OrderStatusHistory, error)
}

type PaymentRepository interface {
	CreatePayment(ctx context.Context, payment *Payment) error
	GetPaymentByOrderID(ctx context.Context, orderID int) (*Payment, error)
	UpdatePayment(ctx context.Context, payment *Payment) error
	CreateRefund(ctx context.Context, refund *Refund) error
	GetRefundByID(ctx context.Context, id int) (*Refund, error)
	UpdateRefund(ctx context.Context, refund *Refund) error
}

type ShipmentRepository interface {
	Create(ctx context.Context, shipment *Shipment) error
	GetByIDForUpdate(ctx context.Context, id int) (*Shipment, error)
	GetByOrderID(ctx context.Context, orderID int) (*Shipment, error)
	GetByTrackingCode(ctx context.Context, trackingCode string) (*Shipment, error)
	Update(ctx context.Context, shipment *Shipment) error
	GetStatusByID(ctx context.Context, id int) (*ShipmentStatus, error)
	GetStatusByCode(ctx context.Context, code string) (*ShipmentStatus, error)

	CreateItems(ctx context.Context, items []ShipmentItem) error
	ListItems(ctx context.Context, shipmentID int) ([]ShipmentItem, error)
	CreateTrackingEvent(ctx context.Context, event *ShipmentTrackingEvent) error
	ListTrackingEvents(ctx context.Context, shipmentID int) ([]ShipmentTrackingEvent, error)
}

type TxManager interface {
	WithTransaction(ctx context.Context, fn func(txCtx context.Context) error) error
}
