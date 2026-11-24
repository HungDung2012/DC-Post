package dto

import "time"

type CreateOrderReq struct {
	CustomID      int            `json:"custom_id"`
	WarehouseID   int            `json:"warehouse_id"`
	PaymentMethod string         `json:"payment_method"`
	Items         []OrderItemReq `json:"items"`
}
type OrderItemReq struct {
	ProductID int `json:"product_id"`
	Quantity  int `json:"quantity"`
}
type OrderListQuery struct {
	Page       int    `form:"page"`
	Limit      int    `form:"limit"`
	StatusCode string `form:"status_code"`
	OrderCode  string `form:"order_code"`
	CustomID   *int   `form:"custom_id"`
	FromDate   string `form:"from_date"`
	ToDate     string `form:"to_date"`
}
type UpdateOrderStatusReq struct {
	NewStatusCode string `json:"new_status_code"`
}
type ConfirmPaymentReq struct {
	TransactionCode string `json:"transaction_code"`
}
type UpdateRefundStatusReq struct {
	RefundStatus string `json:"refund_status"`
}

type StatusResponse struct {
	StatusID   int    `json:"status_id"`
	StatusCode string `json:"status_code"`
	StatusName string `json:"status_name"`
}
type OrderSummary struct {
	OrderID       int            `json:"order_id"`
	OrderCode     string         `json:"order_code"`
	CustomID      int            `json:"custom_id"`
	WarehouseID   int            `json:"warehouse_id"`
	Status        StatusResponse `json:"status"`
	TotalAmount   float64        `json:"total_amount"`
	PaymentMethod string         `json:"payment_method"`
	CreatedAt     time.Time      `json:"created_at"`
}
type OrderResponse struct {
	OrderSummary
	Items   []OrderItemResponse `json:"items"`
	Payment PaymentResponse     `json:"payment"`
}
type OrderDetailResponse struct {
	OrderResponse
	Refunds       []RefundResponse             `json:"refunds"`
	Shipments     []ShipmentResponse           `json:"shipments"`
	StatusHistory []OrderStatusHistoryResponse `json:"status_history"`
}
type OrderItemResponse struct {
	OrderItemID int     `json:"order_item_id"`
	ProductID   int     `json:"product_id"`
	ItemName    string  `json:"item_name"`
	Quantity    int     `json:"quantity"`
	UnitPrice   float64 `json:"unit_price"`
}
type PaymentResponse struct {
	PaymentID       int        `json:"payment_id"`
	Amount          float64    `json:"amount"`
	PaymentMethod   string     `json:"payment_method"`
	PaymentStatus   string     `json:"payment_status"`
	TransactionCode *string    `json:"transaction_code"`
	PaidAt          *time.Time `json:"paid_at"`
}
type RefundResponse struct {
	RefundID     int       `json:"refund_id"`
	PaymentID    int       `json:"payment_id"`
	RefundAmount float64   `json:"refund_amount"`
	RefundStatus string    `json:"refund_status"`
	CreatedAt    time.Time `json:"created_at"`
}
type OrderStatusHistoryResponse struct {
	HistoryID int             `json:"history_id"`
	OrderID   int             `json:"order_id"`
	OldStatus *StatusResponse `json:"old_status"`
	NewStatus StatusResponse  `json:"new_status"`
	ChangedBy int             `json:"changed_by"`
	ChangeAt  time.Time       `json:"change_at"`
}
type OrderStatusChangeResponse struct {
	OrderID       int       `json:"order_id"`
	OrderCode     string    `json:"order_code"`
	OldStatusCode string    `json:"old_status_code"`
	NewStatusCode string    `json:"new_status_code"`
	ChangedBy     int       `json:"changed_by"`
	ChangeAt      time.Time `json:"change_at"`
}
type CancelOrderResponse struct {
	OrderID       int             `json:"order_id"`
	StatusCode    string          `json:"status_code"`
	StockReleased bool            `json:"stock_released"`
	Refund        *RefundResponse `json:"refund"`
}
