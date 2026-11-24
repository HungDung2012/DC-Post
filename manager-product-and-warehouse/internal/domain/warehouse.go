package domain

import "time"

const (
	TxTypeImport  = "IMPORT"
	TxTypeExport  = "EXPORT"
	TxTypeReserve = "RESERVE"
	TxTypeRelease = "RELEASE"
	TxTypeReturn  = "RETURN"
	TxTypeAdjust  = "ADJUST"
)

type Warehouse struct {
	ID      int
	Name    string
	Address string
}

type WarehouseStaff struct {
	WarehouseID int
	UserID      int
}

type Inventory struct {
	ID               int
	WarehouseID      int
	ProductID        int
	Quantity         int
	ReservedQuantity int
}

func (i Inventory) AvailableQuantity() int {
	return i.Quantity - i.ReservedQuantity
}

type InventoryTransaction struct {
	ID              int
	ProductID       int
	WarehouseID     int
	ShipmentID      *int
	PerformedBy     int
	TransactionType string
	Quantity        int
	CreatedAt       time.Time
}
