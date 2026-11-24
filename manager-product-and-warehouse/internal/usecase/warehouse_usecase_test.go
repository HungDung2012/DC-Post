package usecase

import (
	"context"
	"errors"
	"maps"
	"testing"
	"time"

	"dc-post/manager-product-and-warehouse/internal/domain"
	"dc-post/manager-product-and-warehouse/internal/dto"
	"dc-post/manager-product-and-warehouse/pkg/apperror"
)

type warehouseMemory struct {
	domain.WarehouseRepository
	warehouses         map[int]domain.Warehouse
	stock              map[int]domain.Inventory
	staff              map[domain.WarehouseStaff]bool
	log                []domain.InventoryTransaction
	failProduct        int
	txCtx              context.Context
	historyFilter      domain.InventoryTransactionFilter
	historyStaff       int
	scopeInventoryRead bool
	assignmentReadCtx  context.Context
}

func (m *warehouseMemory) WithTransaction(ctx context.Context, fn func(context.Context) error) error {
	txCtx, cancel := context.WithCancel(ctx)
	defer cancel()
	m.txCtx = txCtx
	stock, log := maps.Clone(m.stock), len(m.log)
	if err := fn(txCtx); err != nil {
		m.stock, m.log = stock, m.log[:log]
		return err
	}
	return nil
}

func (m *warehouseMemory) requireTx(ctx context.Context) {
	if ctx != m.txCtx {
		panic("inventory write or lock outside transaction")
	}
}

func (m *warehouseMemory) GetWarehouseByID(_ context.Context, id int) (*domain.Warehouse, error) {
	warehouse, ok := m.warehouses[id]
	if !ok {
		return nil, apperror.ErrResourceNotFound
	}
	return &warehouse, nil
}

func (m *warehouseMemory) CreateWarehouse(_ context.Context, warehouse *domain.Warehouse) error {
	warehouse.ID = len(m.warehouses) + 1
	m.warehouses[warehouse.ID] = *warehouse
	return nil
}

func (m *warehouseMemory) UpdateWarehouse(_ context.Context, warehouse *domain.Warehouse) error {
	m.warehouses[warehouse.ID] = *warehouse
	return nil
}

func (m *warehouseMemory) ListWarehouses(_ context.Context, page domain.Page) ([]domain.Warehouse, int, error) {
	return []domain.Warehouse{m.warehouses[page.Offset+1]}, len(m.warehouses), nil
}

func (m *warehouseMemory) StaffUserExists(_ context.Context, id int) (bool, error) {
	return id == 22, nil
}

func (m *warehouseMemory) AssignStaff(_ context.Context, staff domain.WarehouseStaff) error {
	m.staff[staff] = true
	return nil
}

func (m *warehouseMemory) RemoveStaff(_ context.Context, staff domain.WarehouseStaff) error {
	delete(m.staff, staff)
	return nil
}

func (m *warehouseMemory) HasStaff(ctx context.Context, warehouseID, userID int) (bool, error) {
	m.assignmentReadCtx = ctx
	return m.staff[domain.WarehouseStaff{WarehouseID: warehouseID, UserID: userID}], nil
}

func (m *warehouseMemory) ListInventory(ctx context.Context, _ int, _ domain.InventoryFilter) ([]domain.Inventory, int, error) {
	if m.scopeInventoryRead && (ctx != m.txCtx || ctx != m.assignmentReadCtx) {
		return nil, 0, errors.New("assignment lock expired before inventory read")
	}
	return []domain.Inventory{m.stock[1]}, 1, nil
}

func (m *warehouseMemory) EnsureInventory(ctx context.Context, warehouseID, productID int) error {
	m.requireTx(ctx)
	if _, ok := m.stock[productID]; !ok {
		m.stock[productID] = domain.Inventory{ID: productID, ProductID: productID, WarehouseID: warehouseID}
	}
	return nil
}

func (m *warehouseMemory) LockInventoryForUpdate(ctx context.Context, _ int, ids []int) (map[int]*domain.Inventory, error) {
	m.requireTx(ctx)
	rows := make(map[int]*domain.Inventory)
	for _, id := range ids {
		if item, ok := m.stock[id]; ok {
			rows[id] = &item
		}
	}
	return rows, nil
}

func (m *warehouseMemory) UpdateInventory(ctx context.Context, row *domain.Inventory) error {
	m.requireTx(ctx)
	m.stock[row.ProductID] = *row
	return nil
}

var errAuditWrite = errors.New("audit write failed")

func (m *warehouseMemory) CreateInventoryTransaction(ctx context.Context, entry *domain.InventoryTransaction) error {
	m.requireTx(ctx)
	if entry.ProductID == m.failProduct {
		return errAuditWrite
	}
	entry.ID = len(m.log) + 1
	m.log = append(m.log, *entry)
	return nil
}

func (m *warehouseMemory) ListInventoryTransactions(_ context.Context, filter domain.InventoryTransactionFilter) ([]domain.InventoryTransaction, int, error) {
	m.historyFilter, m.historyStaff = filter, 0
	return m.log, len(m.log), nil
}

func (m *warehouseMemory) ListStaffInventoryTransactions(_ context.Context, userID int, filter domain.InventoryTransactionFilter) ([]domain.InventoryTransaction, int, error) {
	m.historyFilter, m.historyStaff = filter, userID
	return m.log, len(m.log), nil
}

func warehouseFixture() (*WarehouseUsecase, *warehouseMemory) {
	m := &warehouseMemory{
		warehouses: map[int]domain.Warehouse{1: {ID: 1, Name: "Central"}, 2: {ID: 2, Name: "West"}},
		stock:      map[int]domain.Inventory{1: {ID: 1, WarehouseID: 1, ProductID: 1, Quantity: 10, ReservedQuantity: 7}},
		staff:      map[domain.WarehouseStaff]bool{{WarehouseID: 1, UserID: 22}: true},
	}
	products := &productMemory{products: map[int]domain.Product{
		1: {ID: 1, Code: "P-1", Name: "Carton", UnitPrice: 125}, 2: {ID: 2, Code: "P-2", Name: "Tape"},
	}}
	return NewWarehouseUsecase(m, products, m), m
}

var warehouseStaff = Actor{UserID: 22, Role: "STAFF"}
var warehouseAdmin = Actor{UserID: 90, Role: "ADMIN"}

func TestImportStock_CreatesOrIncrementsInventoryAndLogsTransaction(t *testing.T) {
	uc, m := warehouseFixture()
	req := dto.ImportStockReq{WarehouseID: 1, Items: []dto.StockItem{{ProductID: 2, Quantity: 4}, {ProductID: 1, Quantity: 3}}}

	result, err := uc.ImportStock(t.Context(), warehouseStaff, req)
	if err != nil {
		t.Fatal(err)
	}
	if m.stock[1].Quantity != 13 || m.stock[1].ReservedQuantity != 7 || m.stock[2].Quantity != 4 || len(m.log) != 2 {
		t.Fatalf("stock = %+v, log = %+v", m.stock, m.log)
	}
	for i, entry := range m.log {
		item := req.Items[i]
		if entry.TransactionType != "IMPORT" || entry.WarehouseID != 1 || entry.PerformedBy != 22 ||
			entry.ProductID != item.ProductID || entry.Quantity != item.Quantity || entry.ShipmentID != nil {
			t.Fatalf("unexpected import entry: %+v", entry)
		}
	}
	if len(result.Transactions) != 2 || result.Transactions[0].NewStockQuantity != 4 || result.Transactions[1].NewStockQuantity != 13 {
		t.Fatalf("unexpected import result: %+v", result)
	}
}

func TestImportStockRollsBackBatchOnAuditError(t *testing.T) {
	uc, m := warehouseFixture()
	m.failProduct = 2
	req := dto.ImportStockReq{WarehouseID: 1, Items: []dto.StockItem{{ProductID: 1, Quantity: 3}, {ProductID: 2, Quantity: 4}}}
	if _, err := uc.ImportStock(t.Context(), warehouseAdmin, req); !errors.Is(err, errAuditWrite) {
		t.Fatalf("import error = %v", err)
	}
	if m.stock[1].Quantity != 10 || len(m.stock) != 1 || len(m.log) != 0 {
		t.Fatalf("partial import: stock = %+v, log = %+v", m.stock, m.log)
	}
}

func TestExportManualStock_FailsWhenExceedsAvailableStock(t *testing.T) {
	uc, m := warehouseFixture()
	req := dto.ExportStockReq{WarehouseID: 1, Items: []dto.StockItem{{ProductID: 1, Quantity: 2}, {ProductID: 1, Quantity: 2}}}
	if _, err := uc.ExportManualStock(t.Context(), warehouseStaff, req); !errors.Is(err, apperror.ErrInsufficientStock) {
		t.Fatalf("export error = %v", err)
	}
	if m.stock[1].Quantity != 10 || len(m.log) != 0 {
		t.Fatal("insufficient export changed stock")
	}
}

func TestExportManualStockLogsNegativeQuantityWithNoShipment(t *testing.T) {
	uc, m := warehouseFixture()
	req := dto.ExportStockReq{WarehouseID: 1, Items: []dto.StockItem{{ProductID: 1, Quantity: 3}}}
	if _, err := uc.ExportManualStock(t.Context(), warehouseAdmin, req); err != nil {
		t.Fatal(err)
	}
	if m.stock[1].Quantity != 7 || m.stock[1].ReservedQuantity != 7 || len(m.log) != 1 {
		t.Fatalf("stock = %+v, log = %+v", m.stock, m.log)
	}
	entry := m.log[0]
	if entry.TransactionType != "EXPORT" || entry.Quantity != -3 || entry.ShipmentID != nil || entry.PerformedBy != 90 {
		t.Fatalf("unexpected export entry: %+v", entry)
	}
}

func TestAdjustStock_FailsWhenBelowReservedQuantity(t *testing.T) {
	uc, m := warehouseFixture()
	req := dto.AdjustStockReq{WarehouseID: 1, ProductID: 1, ActualQuantity: 6}
	if _, err := uc.AdjustStock(t.Context(), warehouseStaff, req); !errors.Is(err, apperror.ErrBelowReservedStock) {
		t.Fatalf("adjust error = %v", err)
	}
	if m.stock[1].Quantity != 10 || len(m.log) != 0 {
		t.Fatal("rejected count changed stock")
	}
}

func TestAdjustStockLogsSignedDifferenceAtReservedBoundary(t *testing.T) {
	uc, m := warehouseFixture()
	req := dto.AdjustStockReq{WarehouseID: 1, ProductID: 1, ActualQuantity: 7}
	if _, err := uc.AdjustStock(t.Context(), warehouseStaff, req); err != nil {
		t.Fatal(err)
	}
	if m.stock[1].Quantity != 7 || m.stock[1].ReservedQuantity != 7 || len(m.log) != 1 ||
		m.log[0].TransactionType != "ADJUST" || m.log[0].Quantity != -3 {
		t.Fatalf("stock = %+v, log = %+v", m.stock, m.log)
	}
}

func TestStaffWarehouseAccess_DeniedWhenNotAssigned(t *testing.T) {
	uc, m := warehouseFixture()
	ctx := t.Context()
	items := []dto.StockItem{{ProductID: 1, Quantity: 1}}
	id := 2
	_, importErr := uc.ImportStock(ctx, warehouseStaff, dto.ImportStockReq{WarehouseID: id, Items: items})
	_, exportErr := uc.ExportManualStock(ctx, warehouseStaff, dto.ExportStockReq{WarehouseID: id, Items: items})
	_, adjustErr := uc.AdjustStock(ctx, warehouseStaff, dto.AdjustStockReq{WarehouseID: id, ProductID: 1, ActualQuantity: 9})
	_, _, inventoryErr := uc.GetWarehouseInventory(ctx, warehouseStaff, id, dto.InventoryListQuery{})
	_, _, historyErr := uc.ListInventoryTransactions(ctx, warehouseStaff, dto.InventoryTxFilter{WarehouseID: &id})
	for name, err := range map[string]error{"import": importErr, "export": exportErr, "adjust": adjustErr, "inventory": inventoryErr, "history": historyErr} {
		if !errors.Is(err, apperror.ErrWarehouseAccessDenied) {
			t.Errorf("%s: %v", name, err)
		}
	}
	if len(m.log) != 0 {
		t.Fatal("denied mutations wrote transactions")
	}
	if _, _, err := uc.GetWarehouseInventory(ctx, warehouseAdmin, id, dto.InventoryListQuery{}); err != nil {
		t.Fatalf("unassigned administrator: %v", err)
	}
}

func TestWarehouseManagementValidatesStaffAndWarehouse(t *testing.T) {
	uc, m := warehouseFixture()
	ctx := t.Context()
	req := dto.WarehouseReq{WarehouseName: "North", Address: "3 Main St"}
	if _, err := uc.CreateWarehouse(ctx, warehouseStaff, req); !errors.Is(err, apperror.ErrForbidden) {
		t.Fatalf("staff created warehouse: %v", err)
	}
	created, err := uc.CreateWarehouse(ctx, warehouseAdmin, req)
	if err != nil || created.WarehouseID != 3 {
		t.Fatalf("create = %+v, %v", created, err)
	}
	req.WarehouseName = "North Hub"
	updated, err := uc.UpdateWarehouse(ctx, warehouseAdmin, 3, req)
	if err != nil || updated.WarehouseName != "North Hub" {
		t.Fatalf("update = %+v, %v", updated, err)
	}
	rows, total, err := uc.ListWarehouses(ctx, warehouseStaff, dto.WarehouseListQuery{Page: 3, Limit: 1})
	if err != nil || total != 3 || len(rows) != 1 || rows[0].WarehouseID != 3 {
		t.Fatalf("list = %+v, %d, %v", rows, total, err)
	}
	if err := uc.AssignStaff(ctx, warehouseAdmin, 3, dto.AssignStaffReq{UserID: 99}); !errors.Is(err, apperror.ErrResourceNotFound) {
		t.Fatalf("unknown staff assignment: %v", err)
	}
	if err := uc.AssignStaff(ctx, warehouseAdmin, 99, dto.AssignStaffReq{UserID: 22}); !errors.Is(err, apperror.ErrResourceNotFound) {
		t.Fatalf("unknown warehouse assignment: %v", err)
	}
	if err := uc.AssignStaff(ctx, warehouseAdmin, 3, dto.AssignStaffReq{UserID: 22}); err != nil || !m.staff[domain.WarehouseStaff{WarehouseID: 3, UserID: 22}] {
		t.Fatalf("assign staff: %v", err)
	}
	if err := uc.RemoveStaff(ctx, warehouseAdmin, 3, 22); err != nil || m.staff[domain.WarehouseStaff{WarehouseID: 3, UserID: 22}] {
		t.Fatalf("remove staff: %v", err)
	}
}

func TestWarehouseInventoryIncludesProductAndAvailableQuantity(t *testing.T) {
	uc, m := warehouseFixture()
	m.scopeInventoryRead = true
	items, total, err := uc.GetWarehouseInventory(t.Context(), warehouseStaff, 1, dto.InventoryListQuery{})
	if err != nil || total != 1 || len(items) != 1 || items[0].AvailableQuantity != 3 || items[0].WarehouseName != "Central" ||
		items[0].Product.ProductCode != "P-1" || items[0].Product.UnitPrice != 1.25 {
		t.Fatalf("inventory = %+v, %d, %v", items, total, err)
	}
}

func TestTransactionHistoryScopesUnfilteredStaffAndIncludesReferenceDetails(t *testing.T) {
	uc, m := warehouseFixture()
	m.log = []domain.InventoryTransaction{{ID: 10, WarehouseID: 1, ProductID: 1, Quantity: 3, TransactionType: "IMPORT"}}
	filter := dto.InventoryTxFilter{FromDate: "2026-11-23", ToDate: "2026-11-24", TransactionType: "IMPORT"}
	rows, total, err := uc.ListInventoryTransactions(t.Context(), warehouseStaff, filter)
	if err != nil || total != 1 || len(rows) != 1 || rows[0].Warehouse.WarehouseName != "Central" || rows[0].Product.ProductCode != "P-1" {
		t.Fatalf("history = %+v, %d, %v", rows, total, err)
	}
	if m.historyStaff != 22 || m.historyFilter.WarehouseID != nil || m.historyFilter.Limit != 20 ||
		m.historyFilter.From == nil || m.historyFilter.Before == nil ||
		m.historyFilter.From.Format(time.RFC3339) != "2026-11-23T00:00:00Z" ||
		m.historyFilter.Before.Format(time.RFC3339) != "2026-11-25T00:00:00Z" {
		t.Fatalf("staff = %d; filter = %+v", m.historyStaff, m.historyFilter)
	}
	if _, _, err := uc.ListInventoryTransactions(t.Context(), warehouseAdmin, filter); err != nil || m.historyStaff != 0 {
		t.Fatalf("admin history: %v; staff scope = %d", err, m.historyStaff)
	}
}
