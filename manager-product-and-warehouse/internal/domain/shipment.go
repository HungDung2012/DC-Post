package domain

import "time"

const (
	ShipmentStatusCreated        = "CREATED"
	ShipmentStatusInTransit      = "IN_TRANSIT"
	ShipmentStatusOutForDelivery = "OUT_FOR_DELIVERY"
	ShipmentStatusDelivered      = "DELIVERED"
	ShipmentStatusReturned       = "RETURNED"
)

type Shipment struct {
	ID                 int
	OrderID            int
	TrackingCode       string
	ShipperID          *int
	StatusID           int
	CurrentWarehouseID *int
	CODAmount          int64
	DeliveredAt        *time.Time
}

type ShipmentItem struct {
	ID          int
	ShipmentID  int
	OrderItemID int
	Quantity    int
}

type ShipmentStatus struct {
	ID   int
	Code string
	Name string
}

type ShipmentTrackingEvent struct {
	ID              int
	ShipmentID      int
	StatusID        int
	WarehouseID     *int
	ScannedBy       int
	FromWarehouseID *int
	ScannedAt       time.Time
}
