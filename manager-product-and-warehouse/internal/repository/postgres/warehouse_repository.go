package postgres

import (
	"context"
	"errors"
	"math"
	"slices"

	"dc-post/manager-product-and-warehouse/internal/domain"
	"dc-post/manager-product-and-warehouse/pkg/apperror"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type WarehouseRepository struct {
	pool *pgxpool.Pool
}

var _ domain.WarehouseRepository = (*WarehouseRepository)(nil)

func NewWarehouseRepository(pool *pgxpool.Pool) *WarehouseRepository {
	return &WarehouseRepository{pool: pool}
}

const warehouseColumns = "warehouse_id, warehouse_name, address"

func scanWarehouse(row pgx.CollectableRow) (domain.Warehouse, error) {
	var w domain.Warehouse
	err := row.Scan(&w.ID, &w.Name, &w.Address)
	return w, err
}

func (r *WarehouseRepository) CreateWarehouse(ctx context.Context, w *domain.Warehouse) error {
	return GetQuerier(ctx, r.pool).QueryRow(ctx, `
		INSERT INTO warehouses (warehouse_name, address) VALUES ($1, $2)
		RETURNING warehouse_id`, w.Name, w.Address).Scan(&w.ID)
}

func (r *WarehouseRepository) GetWarehouseByID(ctx context.Context, id int) (*domain.Warehouse, error) {
	var w domain.Warehouse
	err := GetQuerier(ctx, r.pool).QueryRow(ctx,
		"SELECT "+warehouseColumns+" FROM warehouses WHERE warehouse_id = $1", id).
		Scan(&w.ID, &w.Name, &w.Address)
	if err != nil {
		return nil, repositoryRowError(err)
	}
	return &w, nil
}

func (r *WarehouseRepository) ListWarehouses(ctx context.Context, page domain.Page) ([]domain.Warehouse, int, error) {
	return queryPage(ctx, GetQuerier(ctx, r.pool), warehouseColumns, " FROM warehouses", "warehouse_id", page, scanWarehouse)
}

func (r *WarehouseRepository) UpdateWarehouse(ctx context.Context, w *domain.Warehouse) error {
	tag, err := GetQuerier(ctx, r.pool).Exec(ctx, `
		UPDATE warehouses SET warehouse_name = $2, address = $3 WHERE warehouse_id = $1`, w.ID, w.Name, w.Address)
	if err == nil && tag.RowsAffected() == 0 {
		return apperror.ErrResourceNotFound
	}
	return err
}

// StaffUserExists checks the user reference in the shared identity table.
func (r *WarehouseRepository) StaffUserExists(ctx context.Context, userID int) (bool, error) {
	var id int
	err := GetQuerier(ctx, r.pool).QueryRow(ctx, "SELECT user_id FROM users WHERE user_id = $1 FOR KEY SHARE", userID).Scan(&id)
	if errors.Is(err, pgx.ErrNoRows) {
		return false, nil
	}
	return err == nil, err
}

func (r *WarehouseRepository) AssignStaff(ctx context.Context, staff domain.WarehouseStaff) error {
	_, err := GetQuerier(ctx, r.pool).Exec(ctx, `
		INSERT INTO warehouse_staff (warehouse_id, user_id) VALUES ($1, $2)
		ON CONFLICT (warehouse_id, user_id) DO NOTHING`, staff.WarehouseID, staff.UserID)
	return err
}

func (r *WarehouseRepository) RemoveStaff(ctx context.Context, staff domain.WarehouseStaff) error {
	_, err := GetQuerier(ctx, r.pool).Exec(ctx,
		"DELETE FROM warehouse_staff WHERE warehouse_id = $1 AND user_id = $2", staff.WarehouseID, staff.UserID)
	return err
}

func (r *WarehouseRepository) HasStaff(ctx context.Context, warehouseID, userID int) (bool, error) {
	var assigned bool
	err := GetQuerier(ctx, r.pool).QueryRow(ctx, `
		SELECT EXISTS (SELECT 1 FROM warehouse_staff
			WHERE warehouse_id = $1 AND user_id = $2 FOR KEY SHARE)`, warehouseID, userID).Scan(&assigned)
	return assigned, err
}

const inventoryColumns = "inventory_id, warehouse_id, product_id, quantity, reserved_quantity"

// Low stock means at most ten available units after reservations.
const lowStockThreshold = 10

func scanInventory(row pgx.CollectableRow) (domain.Inventory, error) {
	var i domain.Inventory
	err := row.Scan(&i.ID, &i.WarehouseID, &i.ProductID, &i.Quantity, &i.ReservedQuantity)
	return i, err
}

func (r *WarehouseRepository) ListInventory(ctx context.Context, warehouseID int, filter domain.InventoryFilter) ([]domain.Inventory, int, error) {
	const matching = ` FROM inventory i JOIN products p ON p.product_id = i.product_id
		WHERE i.warehouse_id = $1
			AND (strpos(lower(p.product_code), lower($2::text)) > 0
				OR strpos(lower(p.product_name), lower($2::text)) > 0)
			AND (NOT $3::boolean OR i.quantity - i.reserved_quantity <= $4)`
	const columns = "i.inventory_id, i.warehouse_id, i.product_id, i.quantity, i.reserved_quantity"
	return queryPage(ctx, GetQuerier(ctx, r.pool), columns, matching, "product_id", filter.Page, scanInventory,
		warehouseID, filter.Search, filter.LowStock, lowStockThreshold)
}

func (r *WarehouseRepository) LockInventoryForUpdate(ctx context.Context, warehouseID int, productIDs []int) (map[int]*domain.Inventory, error) {
	if _, ok := ctx.Value(txContextKey{}).(pgx.Tx); !ok {
		return nil, errors.New("locking inventory requires a transaction")
	}
	inventory := make(map[int]*domain.Inventory)
	if len(productIDs) == 0 {
		return inventory, nil
	}
	ordered := slices.Clone(productIDs)
	slices.Sort(ordered)
	ids := make([]int32, 0, len(ordered))
	for _, id := range slices.Compact(ordered) {
		if id <= 0 || id > math.MaxInt32 {
			return nil, apperror.ErrInvalidRequest
		}
		ids = append(ids, int32(id))
	}
	rows, err := GetQuerier(ctx, r.pool).Query(ctx, "SELECT "+inventoryColumns+`
		FROM inventory WHERE warehouse_id = $1 AND product_id = ANY($2::integer[])
		ORDER BY product_id FOR UPDATE`, warehouseID, ids)
	if err != nil {
		return nil, err
	}
	items, err := pgx.CollectRows(rows, scanInventory)
	if err != nil {
		return nil, err
	}
	for i := range items {
		item := &items[i]
		inventory[item.ProductID] = item
	}
	return inventory, nil
}

func (r *WarehouseRepository) EnsureInventory(ctx context.Context, warehouseID, productID int) error {
	_, err := GetQuerier(ctx, r.pool).Exec(ctx, `
		INSERT INTO inventory (warehouse_id, product_id) VALUES ($1, $2)
		ON CONFLICT (warehouse_id, product_id) DO NOTHING`, warehouseID, productID)
	return err
}

func (r *WarehouseRepository) UpdateInventory(ctx context.Context, i *domain.Inventory) error {
	tag, err := GetQuerier(ctx, r.pool).Exec(ctx, `
		UPDATE inventory SET quantity = $3, reserved_quantity = $4
		WHERE warehouse_id = $1 AND product_id = $2`, i.WarehouseID, i.ProductID, i.Quantity, i.ReservedQuantity)
	if err == nil && tag.RowsAffected() == 0 {
		return apperror.ErrResourceNotFound
	}
	return err
}

func (r *WarehouseRepository) CreateInventoryTransaction(ctx context.Context, tx *domain.InventoryTransaction) error {
	return GetQuerier(ctx, r.pool).QueryRow(ctx, `
		INSERT INTO inventory_transactions
			(product_id, warehouse_id, shipment_id, performed_by, transaction_type, quantity)
		VALUES ($1, $2, $3, $4, $5, $6) RETURNING transaction_id, created_at`,
		tx.ProductID, tx.WarehouseID, tx.ShipmentID, tx.PerformedBy, tx.TransactionType, tx.Quantity).
		Scan(&tx.ID, &tx.CreatedAt)
}

func (r *WarehouseRepository) ListInventoryTransactions(ctx context.Context, filter domain.InventoryTransactionFilter) ([]domain.InventoryTransaction, int, error) {
	return r.listInventoryTransactions(ctx, filter, nil)
}

// ListStaffInventoryTransactions scopes results and the total before pagination.
func (r *WarehouseRepository) ListStaffInventoryTransactions(ctx context.Context, userID int, filter domain.InventoryTransactionFilter) ([]domain.InventoryTransaction, int, error) {
	return r.listInventoryTransactions(ctx, filter, &userID)
}

func (r *WarehouseRepository) listInventoryTransactions(ctx context.Context, filter domain.InventoryTransactionFilter, staffID *int) ([]domain.InventoryTransaction, int, error) {
	const columns = "transaction_id, product_id, warehouse_id, shipment_id, performed_by, transaction_type, quantity, created_at"
	const matching = ` FROM inventory_transactions it WHERE
		($1::integer IS NULL OR it.warehouse_id = $1) AND
		($2::integer IS NULL OR it.product_id = $2) AND
		($3::integer IS NULL OR it.shipment_id = $3) AND
		($4::integer IS NULL OR it.performed_by = $4) AND
		($5::text = '' OR it.transaction_type = $5) AND
		($6::timestamptz IS NULL OR it.created_at >= $6) AND
		($7::timestamptz IS NULL OR it.created_at < $7) AND
		($8::integer IS NULL OR EXISTS (
			SELECT 1 FROM warehouse_staff ws WHERE ws.warehouse_id = it.warehouse_id AND ws.user_id = $8
		))`
	return queryPage(ctx, GetQuerier(ctx, r.pool), columns, matching, "created_at DESC, transaction_id DESC", filter.Page,
		scanInventoryTransaction, filter.WarehouseID, filter.ProductID, filter.ShipmentID, filter.PerformedBy,
		filter.TransactionType, filter.From, filter.Before, staffID)
}

func scanInventoryTransaction(row pgx.CollectableRow) (domain.InventoryTransaction, error) {
	var tx domain.InventoryTransaction
	err := row.Scan(&tx.ID, &tx.ProductID, &tx.WarehouseID, &tx.ShipmentID, &tx.PerformedBy,
		&tx.TransactionType, &tx.Quantity, &tx.CreatedAt)
	return tx, err
}
