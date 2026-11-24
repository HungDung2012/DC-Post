package usecase

import (
	"context"
	"math"
	"slices"
	"strings"
	"time"

	"dc-post/manager-product-and-warehouse/internal/domain"
	"dc-post/manager-product-and-warehouse/internal/dto"
	"dc-post/manager-product-and-warehouse/pkg/apperror"
)

// WarehouseStore adds the UC02 identity lookup and staff-scoped history to
// the stock repository shared with orders and shipments.
type WarehouseStore interface {
	domain.WarehouseRepository
	StaffUserExists(ctx context.Context, userID int) (bool, error)
	ListStaffInventoryTransactions(ctx context.Context, userID int, filter domain.InventoryTransactionFilter) ([]domain.InventoryTransaction, int, error)
}

type WarehouseUsecase struct {
	warehouses WarehouseStore
	products   domain.ProductRepository
	tx         domain.TxManager
}

func NewWarehouseUsecase(warehouses WarehouseStore, products domain.ProductRepository, tx domain.TxManager) *WarehouseUsecase {
	return &WarehouseUsecase{warehouses: warehouses, products: products, tx: tx}
}

func (uc *WarehouseUsecase) CreateWarehouse(ctx context.Context, actor Actor, req dto.WarehouseReq) (dto.WarehouseResponse, error) {
	if err := requireAdmin(actor); err != nil {
		return dto.WarehouseResponse{}, err
	}
	warehouse, err := warehouseInput(req)
	if err != nil {
		return dto.WarehouseResponse{}, err
	}
	if err := uc.warehouses.CreateWarehouse(ctx, &warehouse); err != nil {
		return dto.WarehouseResponse{}, err
	}
	return warehouseResponse(warehouse), nil
}

func (uc *WarehouseUsecase) ListWarehouses(ctx context.Context, actor Actor, query dto.WarehouseListQuery) ([]dto.WarehouseResponse, int, error) {
	if err := requireWarehouseRole(actor); err != nil {
		return nil, 0, err
	}
	page, err := listPage(query.Page, query.Limit)
	if err != nil {
		return nil, 0, err
	}
	warehouses, total, err := uc.warehouses.ListWarehouses(ctx, page)
	if err != nil {
		return nil, 0, err
	}
	result := make([]dto.WarehouseResponse, 0, len(warehouses))
	for _, warehouse := range warehouses {
		result = append(result, warehouseResponse(warehouse))
	}
	return result, total, nil
}

func (uc *WarehouseUsecase) UpdateWarehouse(ctx context.Context, actor Actor, id int, req dto.WarehouseReq) (dto.WarehouseResponse, error) {
	if err := requireAdmin(actor); err != nil {
		return dto.WarehouseResponse{}, err
	}
	warehouse, err := warehouseInput(req)
	if err != nil || !validID(id) {
		return dto.WarehouseResponse{}, apperror.ErrInvalidRequest
	}
	warehouse.ID = id
	if err := uc.warehouses.UpdateWarehouse(ctx, &warehouse); err != nil {
		return dto.WarehouseResponse{}, err
	}
	return warehouseResponse(warehouse), nil
}

func warehouseInput(req dto.WarehouseReq) (domain.Warehouse, error) {
	name, address := strings.TrimSpace(req.WarehouseName), strings.TrimSpace(req.Address)
	if !validCatalogText(name) || !validCatalogText(address) {
		return domain.Warehouse{}, apperror.ErrInvalidRequest
	}
	return domain.Warehouse{Name: name, Address: address}, nil
}

func warehouseSummary(warehouse domain.Warehouse) dto.WarehouseSummary {
	return dto.WarehouseSummary{WarehouseID: warehouse.ID, WarehouseName: warehouse.Name}
}

func warehouseResponse(warehouse domain.Warehouse) dto.WarehouseResponse {
	return dto.WarehouseResponse{WarehouseSummary: warehouseSummary(warehouse), Address: warehouse.Address}
}

func (uc *WarehouseUsecase) AssignStaff(ctx context.Context, actor Actor, warehouseID int, req dto.AssignStaffReq) error {
	if err := requireAdmin(actor); err != nil {
		return err
	}
	if !validID(warehouseID) || !validID(req.UserID) {
		return apperror.ErrInvalidRequest
	}
	return uc.tx.WithTransaction(ctx, func(ctx context.Context) error {
		if _, err := uc.warehouses.GetWarehouseByID(ctx, warehouseID); err != nil {
			return err
		}
		exists, err := uc.warehouses.StaffUserExists(ctx, req.UserID)
		if err != nil {
			return err
		}
		if !exists {
			return apperror.ErrResourceNotFound
		}
		return uc.warehouses.AssignStaff(ctx, domain.WarehouseStaff{WarehouseID: warehouseID, UserID: req.UserID})
	})
}

func (uc *WarehouseUsecase) RemoveStaff(ctx context.Context, actor Actor, warehouseID, userID int) error {
	if err := requireAdmin(actor); err != nil {
		return err
	}
	if !validID(warehouseID) || !validID(userID) {
		return apperror.ErrInvalidRequest
	}
	if _, err := uc.warehouses.GetWarehouseByID(ctx, warehouseID); err != nil {
		return err
	}
	return uc.warehouses.RemoveStaff(ctx, domain.WarehouseStaff{WarehouseID: warehouseID, UserID: userID})
}

// accessibleWarehouse checks the target assignment before looking up the
// warehouse so an unassigned staff member cannot discover its existence.
func (uc *WarehouseUsecase) accessibleWarehouse(ctx context.Context, actor Actor, warehouseID int) (*domain.Warehouse, error) {
	if err := requireWarehouseRole(actor); err != nil {
		return nil, err
	}
	if !validID(warehouseID) {
		return nil, apperror.ErrInvalidRequest
	}
	if actor.Role == RoleStaff {
		assigned, err := uc.warehouses.HasStaff(ctx, warehouseID, actor.UserID)
		if err != nil {
			return nil, err
		}
		if !assigned {
			return nil, apperror.ErrWarehouseAccessDenied
		}
	}
	return uc.warehouses.GetWarehouseByID(ctx, warehouseID)
}

func (uc *WarehouseUsecase) GetWarehouseInventory(ctx context.Context, actor Actor, warehouseID int, query dto.InventoryListQuery) ([]dto.InventoryResponse, int, error) {
	if err := requireWarehouseRole(actor); err != nil {
		return nil, 0, err
	}
	page, err := listPage(query.Page, query.Limit)
	if err != nil {
		return nil, 0, err
	}
	filter := domain.InventoryFilter{Page: page, Search: strings.TrimSpace(query.Search), LowStock: query.LowStock}
	var result []dto.InventoryResponse
	var total int
	err = uc.tx.WithTransaction(ctx, func(ctx context.Context) error {
		var err error
		result, total, err = uc.readWarehouseInventory(ctx, actor, warehouseID, filter)
		return err
	})
	if err != nil {
		return nil, 0, err
	}
	return result, total, nil
}

func (uc *WarehouseUsecase) readWarehouseInventory(ctx context.Context, actor Actor, warehouseID int, filter domain.InventoryFilter) ([]dto.InventoryResponse, int, error) {
	warehouse, err := uc.accessibleWarehouse(ctx, actor, warehouseID)
	if err != nil {
		return nil, 0, err
	}
	rows, total, err := uc.warehouses.ListInventory(ctx, warehouseID, filter)
	if err != nil {
		return nil, 0, err
	}
	result := make([]dto.InventoryResponse, 0, len(rows))
	for _, row := range rows {
		product, err := uc.products.GetByID(ctx, row.ProductID)
		if err != nil {
			return nil, 0, err
		}
		result = append(result, dto.InventoryResponse{
			InventoryID: row.ID, WarehouseID: row.WarehouseID, WarehouseName: warehouse.Name,
			Product: productResponse(*product), Quantity: row.Quantity, ReservedQuantity: row.ReservedQuantity,
			AvailableQuantity: row.AvailableQuantity(),
		})
	}
	return result, total, nil
}

func (uc *WarehouseUsecase) ImportStock(ctx context.Context, actor Actor, req dto.ImportStockReq) (dto.StockChangeResponse, error) {
	return uc.moveStock(ctx, actor, req.WarehouseID, req.Items, domain.TxTypeImport)
}

func (uc *WarehouseUsecase) ExportManualStock(ctx context.Context, actor Actor, req dto.ExportStockReq) (dto.StockChangeResponse, error) {
	return uc.moveStock(ctx, actor, req.WarehouseID, req.Items, domain.TxTypeExport)
}

func (uc *WarehouseUsecase) moveStock(ctx context.Context, actor Actor, warehouseID int, items []dto.StockItem, transactionType string) (dto.StockChangeResponse, error) {
	if err := requireWarehouseRole(actor); err != nil {
		return dto.StockChangeResponse{}, err
	}
	if !validID(warehouseID) || len(items) == 0 {
		return dto.StockChangeResponse{}, apperror.ErrInvalidRequest
	}
	ids := make([]int, 0, len(items))
	for _, item := range items {
		if !validID(item.ProductID) || item.Quantity <= 0 || item.Quantity > math.MaxInt32 {
			return dto.StockChangeResponse{}, apperror.ErrInvalidRequest
		}
		ids = append(ids, item.ProductID)
	}
	slices.Sort(ids)
	ids = slices.Compact(ids)

	result := dto.StockChangeResponse{WarehouseID: warehouseID, PerformedBy: actor.UserID, Transactions: make([]dto.StockChange, 0, len(items))}
	err := uc.tx.WithTransaction(ctx, func(ctx context.Context) error {
		if _, err := uc.accessibleWarehouse(ctx, actor, warehouseID); err != nil {
			return err
		}
		for _, id := range ids {
			if _, err := uc.products.GetByID(ctx, id); err != nil {
				return err
			}
		}
		rows, err := uc.lockStock(ctx, warehouseID, ids, transactionType == domain.TxTypeImport)
		if err != nil {
			return err
		}

		for _, item := range items {
			row := rows[item.ProductID]
			delta := item.Quantity
			switch transactionType {
			case domain.TxTypeExport:
				if row == nil || int64(row.Quantity)-int64(row.ReservedQuantity) < int64(item.Quantity) {
					return apperror.ErrInsufficientStock
				}
				delta = -item.Quantity
			case domain.TxTypeImport:
				if row == nil {
					return apperror.ErrResourceNotFound
				}
				if int64(row.Quantity)+int64(delta) > math.MaxInt32 {
					return apperror.ErrInvalidRequest
				}
			}
			change, err := uc.recordStockChange(ctx, row, row.Quantity+delta, actor.UserID, transactionType)
			if err != nil {
				return err
			}
			result.Transactions = append(result.Transactions, change)
		}
		return nil
	})
	if err != nil {
		return dto.StockChangeResponse{}, err
	}
	return result, nil
}

func (uc *WarehouseUsecase) lockStock(ctx context.Context, warehouseID int, sortedIDs []int, create bool) (map[int]*domain.Inventory, error) {
	if !create {
		return uc.warehouses.LockInventoryForUpdate(ctx, warehouseID, sortedIDs)
	}
	rows := make(map[int]*domain.Inventory, len(sortedIDs))
	// An insert can also wait on a concurrent writer. Acquire new and existing
	// rows in one ascending sequence so imports use the same order as exports.
	for i, id := range sortedIDs {
		if err := uc.warehouses.EnsureInventory(ctx, warehouseID, id); err != nil {
			return nil, err
		}
		locked, err := uc.warehouses.LockInventoryForUpdate(ctx, warehouseID, sortedIDs[i:i+1])
		if err != nil {
			return nil, err
		}
		rows[id] = locked[id]
	}
	return rows, nil
}

func (uc *WarehouseUsecase) AdjustStock(ctx context.Context, actor Actor, req dto.AdjustStockReq) (dto.StockChangeResponse, error) {
	if err := requireWarehouseRole(actor); err != nil {
		return dto.StockChangeResponse{}, err
	}
	if !validID(req.WarehouseID) || !validID(req.ProductID) || req.ActualQuantity < 0 || req.ActualQuantity > math.MaxInt32 {
		return dto.StockChangeResponse{}, apperror.ErrInvalidRequest
	}
	var change dto.StockChange
	err := uc.tx.WithTransaction(ctx, func(ctx context.Context) error {
		if _, err := uc.accessibleWarehouse(ctx, actor, req.WarehouseID); err != nil {
			return err
		}
		if _, err := uc.products.GetByID(ctx, req.ProductID); err != nil {
			return err
		}
		rows, err := uc.warehouses.LockInventoryForUpdate(ctx, req.WarehouseID, []int{req.ProductID})
		if err != nil {
			return err
		}
		row := rows[req.ProductID]
		if row == nil {
			return apperror.ErrResourceNotFound
		}
		if req.ActualQuantity < row.ReservedQuantity {
			return apperror.ErrBelowReservedStock
		}
		change, err = uc.recordStockChange(ctx, row, req.ActualQuantity, actor.UserID, domain.TxTypeAdjust)
		return err
	})
	if err != nil {
		return dto.StockChangeResponse{}, err
	}
	return dto.StockChangeResponse{WarehouseID: req.WarehouseID, PerformedBy: actor.UserID, Transactions: []dto.StockChange{change}}, nil
}

func (uc *WarehouseUsecase) recordStockChange(ctx context.Context, row *domain.Inventory, newQuantity, userID int, transactionType string) (dto.StockChange, error) {
	delta := int64(newQuantity) - int64(row.Quantity)
	if delta < math.MinInt32 || delta > math.MaxInt32 {
		return dto.StockChange{}, apperror.ErrInvalidRequest
	}
	row.Quantity = newQuantity
	if err := uc.warehouses.UpdateInventory(ctx, row); err != nil {
		return dto.StockChange{}, err
	}
	entry := domain.InventoryTransaction{
		ProductID: row.ProductID, WarehouseID: row.WarehouseID, ShipmentID: nil,
		PerformedBy: userID, TransactionType: transactionType, Quantity: int(delta),
	}
	if err := uc.warehouses.CreateInventoryTransaction(ctx, &entry); err != nil {
		return dto.StockChange{}, err
	}
	return dto.StockChange{
		TransactionID: entry.ID, ProductID: entry.ProductID, TransactionType: entry.TransactionType,
		Quantity: entry.Quantity, NewStockQuantity: newQuantity, CreatedAt: entry.CreatedAt,
	}, nil
}

func (uc *WarehouseUsecase) ListInventoryTransactions(ctx context.Context, actor Actor, query dto.InventoryTxFilter) ([]dto.InventoryTransactionResponse, int, error) {
	if err := requireWarehouseRole(actor); err != nil {
		return nil, 0, err
	}
	filter, err := inventoryTransactionFilter(query)
	if err != nil {
		return nil, 0, err
	}
	warehouses := make(map[int]dto.WarehouseSummary)
	if query.WarehouseID != nil {
		warehouse, err := uc.accessibleWarehouse(ctx, actor, *query.WarehouseID)
		if err != nil {
			return nil, 0, err
		}
		warehouses[warehouse.ID] = warehouseSummary(*warehouse)
	}

	var rows []domain.InventoryTransaction
	var total int
	if actor.Role == RoleStaff {
		rows, total, err = uc.warehouses.ListStaffInventoryTransactions(ctx, actor.UserID, filter)
	} else {
		rows, total, err = uc.warehouses.ListInventoryTransactions(ctx, filter)
	}
	if err != nil {
		return nil, 0, err
	}
	products := make(map[int]dto.ProductSummary)
	result := make([]dto.InventoryTransactionResponse, 0, len(rows))
	for _, row := range rows {
		warehouse, ok := warehouses[row.WarehouseID]
		if !ok {
			record, err := uc.warehouses.GetWarehouseByID(ctx, row.WarehouseID)
			if err != nil {
				return nil, 0, err
			}
			warehouse = warehouseSummary(*record)
			warehouses[row.WarehouseID] = warehouse
		}
		product, ok := products[row.ProductID]
		if !ok {
			record, err := uc.products.GetByID(ctx, row.ProductID)
			if err != nil {
				return nil, 0, err
			}
			product = productSummary(*record)
			products[row.ProductID] = product
		}
		result = append(result, dto.InventoryTransactionResponse{
			TransactionID: row.ID, Warehouse: warehouse, Product: product, ShipmentID: row.ShipmentID,
			PerformedBy: row.PerformedBy, TransactionType: row.TransactionType, Quantity: row.Quantity, CreatedAt: row.CreatedAt,
		})
	}
	return result, total, nil
}

func inventoryTransactionFilter(query dto.InventoryTxFilter) (domain.InventoryTransactionFilter, error) {
	page, err := listPage(query.Page, query.Limit)
	if err != nil {
		return domain.InventoryTransactionFilter{}, err
	}
	for _, id := range []*int{query.WarehouseID, query.ProductID, query.ShipmentID, query.PerformedBy} {
		if id != nil && !validID(*id) {
			return domain.InventoryTransactionFilter{}, apperror.ErrInvalidRequest
		}
	}
	switch query.TransactionType {
	case "", domain.TxTypeImport, domain.TxTypeExport, domain.TxTypeReserve, domain.TxTypeRelease, domain.TxTypeReturn, domain.TxTypeAdjust:
	default:
		return domain.InventoryTransactionFilter{}, apperror.ErrInvalidRequest
	}
	filter := domain.InventoryTransactionFilter{
		Page: page, WarehouseID: query.WarehouseID, ProductID: query.ProductID, ShipmentID: query.ShipmentID,
		PerformedBy: query.PerformedBy, TransactionType: query.TransactionType,
	}
	if query.FromDate != "" {
		from, err := time.Parse(time.DateOnly, query.FromDate)
		if err != nil {
			return domain.InventoryTransactionFilter{}, apperror.ErrInvalidRequest
		}
		filter.From = &from
	}
	if query.ToDate != "" {
		to, err := time.Parse(time.DateOnly, query.ToDate)
		if err != nil {
			return domain.InventoryTransactionFilter{}, apperror.ErrInvalidRequest
		}
		before := to.AddDate(0, 0, 1)
		filter.Before = &before
	}
	if filter.From != nil && filter.Before != nil && !filter.From.Before(*filter.Before) {
		return domain.InventoryTransactionFilter{}, apperror.ErrInvalidRequest
	}
	return filter, nil
}
