package dto

import (
	"encoding/json"
	"errors"
	"time"
)

type WarehouseReq struct {
	WarehouseName string `json:"warehouse_name" binding:"required,max=255"`
	Address       string `json:"address" binding:"required,max=255"`
}

type WarehouseListQuery struct {
	Page  int `form:"page"`
	Limit int `form:"limit"`
}

type AssignStaffReq struct {
	UserID int `json:"user_id" binding:"required,gt=0"`
}

type WarehouseSummary struct {
	WarehouseID   int    `json:"warehouse_id"`
	WarehouseName string `json:"warehouse_name"`
}

type WarehouseResponse struct {
	WarehouseSummary
	Address string `json:"address"`
}

type InventoryListQuery struct {
	Search   string `form:"search"`
	LowStock bool   `form:"low_stock"`
	Page     int    `form:"page"`
	Limit    int    `form:"limit"`
}

type InventoryResponse struct {
	InventoryID       int             `json:"inventory_id"`
	WarehouseID       int             `json:"warehouse_id"`
	WarehouseName     string          `json:"warehouse_name"`
	Product           ProductResponse `json:"product"`
	Quantity          int             `json:"quantity"`
	ReservedQuantity  int             `json:"reserved_quantity"`
	AvailableQuantity int             `json:"available_quantity"`
}

type StockItem struct {
	ProductID int `json:"product_id" binding:"required,gt=0"`
	Quantity  int `json:"quantity" binding:"required,gt=0"`
}

type ImportStockReq struct {
	WarehouseID int         `json:"warehouse_id" binding:"required,gt=0"`
	Items       []StockItem `json:"items" binding:"required,min=1,dive"`
}

type ExportStockReq struct {
	WarehouseID int         `json:"warehouse_id" binding:"required,gt=0"`
	Items       []StockItem `json:"items" binding:"required,min=1,dive"`
}

type AdjustStockReq struct {
	WarehouseID    int `json:"warehouse_id" binding:"required,gt=0"`
	ProductID      int `json:"product_id" binding:"required,gt=0"`
	ActualQuantity int `json:"actual_quantity" binding:"gte=0"`
}

// UnmarshalJSON keeps zero a valid count while detecting an omitted or null count.
func (req *AdjustStockReq) UnmarshalJSON(data []byte) error {
	type request AdjustStockReq
	payload := struct {
		*request
		ActualQuantity *int `json:"actual_quantity"`
	}{request: (*request)(req)}
	if err := json.Unmarshal(data, &payload); err != nil {
		return err
	}
	if payload.ActualQuantity == nil {
		return errors.New("actual_quantity is required")
	}
	req.ActualQuantity = *payload.ActualQuantity
	return nil
}

type StockChange struct {
	TransactionID    int       `json:"transaction_id"`
	ProductID        int       `json:"product_id"`
	TransactionType  string    `json:"transaction_type"`
	Quantity         int       `json:"quantity"`
	NewStockQuantity int       `json:"new_stock_quantity"`
	CreatedAt        time.Time `json:"created_at"`
}

type StockChangeResponse struct {
	WarehouseID  int           `json:"warehouse_id"`
	PerformedBy  int           `json:"performed_by"`
	Transactions []StockChange `json:"transactions"`
}

type InventoryTxFilter struct {
	WarehouseID     *int   `form:"warehouse_id"`
	ProductID       *int   `form:"product_id"`
	ShipmentID      *int   `form:"shipment_id"`
	PerformedBy     *int   `form:"performed_by"`
	TransactionType string `form:"transaction_type"`
	FromDate        string `form:"from_date"`
	ToDate          string `form:"to_date"`
	Page            int    `form:"page"`
	Limit           int    `form:"limit"`
}

type InventoryTransactionResponse struct {
	TransactionID   int              `json:"transaction_id"`
	Warehouse       WarehouseSummary `json:"warehouse"`
	Product         ProductSummary   `json:"product"`
	ShipmentID      *int             `json:"shipment_id"`
	PerformedBy     int              `json:"performed_by"`
	TransactionType string           `json:"transaction_type"`
	Quantity        int              `json:"quantity"`
	CreatedAt       time.Time        `json:"created_at"`
}
