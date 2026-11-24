package dto

import "time"

type CreateShipmentReq struct {
	OrderID   int  `json:"order_id"`
	ShipperID *int `json:"shipper_id"`
}
type ScanTrackingEventReq struct {
	StatusCode      string `json:"status_code"`
	FromWarehouseID *int   `json:"from_warehouse_id"`
	WarehouseID     *int   `json:"warehouse_id"`
}
type ShipmentResponse struct {
	ShipmentID         int            `json:"shipment_id"`
	OrderID            int            `json:"order_id"`
	TrackingCode       string         `json:"tracking_code"`
	ShipperID          *int           `json:"shipper_id"`
	Status             StatusResponse `json:"status"`
	CurrentWarehouseID *int           `json:"current_warehouse_id"`
	CODAmount          float64        `json:"cod_amount"`
	DeliveredAt        *time.Time     `json:"delivered_at"`
}
type ShipmentTrackingResponse struct {
	ShipmentID       int                     `json:"shipment_id"`
	TrackingCode     string                  `json:"tracking_code"`
	OrderID          int                     `json:"order_id"`
	CurrentStatus    string                  `json:"current_status"`
	CurrentWarehouse *WarehouseSummary       `json:"current_warehouse"`
	DeliveredAt      *time.Time              `json:"delivered_at"`
	Events           []TrackingEventResponse `json:"events"`
}
type TrackingEventResponse struct {
	EventID         int       `json:"event_id"`
	StatusCode      string    `json:"status_code"`
	StatusName      string    `json:"status_name"`
	FromWarehouseID *int      `json:"from_warehouse_id"`
	WarehouseID     *int      `json:"warehouse_id"`
	ScannedBy       int       `json:"scanned_by"`
	ScannedAt       time.Time `json:"scanned_at"`
}
