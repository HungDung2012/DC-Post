package domain

import "time"

const (
	StatusPending    = "PENDING"
	StatusConfirmed  = "CONFIRMED"
	StatusProcessing = "PROCESSING"
	StatusShipping   = "SHIPPING"
	StatusDelivered  = "DELIVERED"
	StatusCancelled  = "CANCELLED"
	StatusReturned   = "RETURNED"
)

type Order struct {
	ID            int
	Code          string
	CustomerID    int // Stored as custom_id.
	WarehouseID   int
	StatusID      int
	TotalAmount   int64
	PaymentMethod string
	CreatedAt     time.Time
}

type OrderItem struct {
	ID        int
	OrderID   int
	ProductID int
	ItemName  string
	Quantity  int
	UnitPrice int64
}

type OrderStatus struct {
	ID   int
	Code string
	Name string
}

type OrderStatusHistory struct {
	ID          int
	OrderID     int
	OldStatusID *int
	NewStatusID int
	ChangedBy   int
	ChangeAt    time.Time
}

// CanUpdateOrderStatus reports whether the status can change through the
// order status update endpoint.
func CanUpdateOrderStatus(from, to string) bool {
	return (from == StatusPending && to == StatusConfirmed) ||
		(from == StatusConfirmed && to == StatusProcessing)
}

// CanTransitionOrderStatus reports whether the order lifecycle allows the
// transition, including transitions driven by cancellation and shipment.
func CanTransitionOrderStatus(from, to string) bool {
	switch to {
	case StatusCancelled:
		return from == StatusPending || from == StatusConfirmed
	case StatusShipping:
		return from == StatusProcessing
	case StatusDelivered, StatusReturned:
		return from == StatusShipping
	default:
		return CanUpdateOrderStatus(from, to)
	}
}
