package domain

import "time"

const (
	PaymentMethodCOD          = "COD"
	PaymentMethodBankTransfer = "BANK_TRANSFER"
	PaymentMethodEWallet      = "E_WALLET"
)

const (
	PaymentStatusPending  = "PENDING"
	PaymentStatusPaid     = "PAID"
	PaymentStatusFailed   = "FAILED"
	PaymentStatusRefunded = "REFUNDED"
)

const (
	RefundStatusPending   = "PENDING"
	RefundStatusCompleted = "COMPLETED"
	RefundStatusRejected  = "REJECTED"
)

type Payment struct {
	ID              int
	OrderID         int
	Amount          int64
	PaymentMethod   string
	PaymentStatus   string
	TransactionCode *string
	PaidAt          *time.Time
}

type Refund struct {
	ID           int
	PaymentID    int
	RefundAmount int64
	RefundStatus string
	CreatedAt    time.Time
}
