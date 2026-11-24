package postgres

import (
	"context"
	"errors"

	"dc-post/manager-product-and-warehouse/internal/domain"
	"dc-post/manager-product-and-warehouse/pkg/apperror"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

type OrderRepository struct{ pool *pgxpool.Pool }

var _ domain.OrderRepository = (*OrderRepository)(nil)

func NewOrderRepository(pool *pgxpool.Pool) *OrderRepository { return &OrderRepository{pool: pool} }

const orderColumns = "order_id, order_code, custom_id, warehouse_id, status_id, (total_amount * 100)::bigint AS total_hundredths, payment_method, created_at"

func scanOrder(row pgx.Row) (domain.Order, error) {
	var o domain.Order
	err := row.Scan(&o.ID, &o.Code, &o.CustomerID, &o.WarehouseID, &o.StatusID, &o.TotalAmount, &o.PaymentMethod, &o.CreatedAt)
	return o, err
}

func (r *OrderRepository) Create(ctx context.Context, o *domain.Order) error {
	err := GetQuerier(ctx, r.pool).QueryRow(ctx, `
  INSERT INTO orders (order_code, custom_id, warehouse_id, status_id, total_amount, payment_method)
  VALUES ($1,$2,$3,$4,$5::bigint::numeric / 100,$6) RETURNING order_id, created_at`,
		o.Code, o.CustomerID, o.WarehouseID, o.StatusID, o.TotalAmount, o.PaymentMethod).Scan(&o.ID, &o.CreatedAt)
	return businessCodeError(err, "orders_order_code_key")
}

func businessCodeError(err error, constraint string) error {
	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) && pgErr.Code == "23505" && pgErr.ConstraintName == constraint {
		return apperror.ErrDuplicateCode
	}
	return err
}

func (r *OrderRepository) GetByID(ctx context.Context, id int) (*domain.Order, error) {
	return r.getOrder(ctx, id, "")
}

func (r *OrderRepository) GetByIDForUpdate(ctx context.Context, id int) (*domain.Order, error) {
	return r.LockOrderForUpdate(ctx, id)
}

func (r *OrderRepository) LockOrderForUpdate(ctx context.Context, id int) (*domain.Order, error) {
	if _, ok := ctx.Value(txContextKey{}).(pgx.Tx); !ok {
		return nil, errors.New("locking an order requires a transaction")
	}
	return r.getOrder(ctx, id, " FOR UPDATE")
}

func (r *OrderRepository) getOrder(ctx context.Context, id int, lock string) (*domain.Order, error) {
	o, err := scanOrder(GetQuerier(ctx, r.pool).QueryRow(ctx, "SELECT "+orderColumns+" FROM orders WHERE order_id = $1"+lock, id))
	if err != nil {
		return nil, repositoryRowError(err)
	}
	return &o, nil
}

func (r *OrderRepository) List(ctx context.Context, filter domain.OrderFilter) ([]domain.Order, int, error) {
	return r.list(ctx, filter, nil)
}

func (r *OrderRepository) ListStaffOrders(ctx context.Context, userID int, filter domain.OrderFilter) ([]domain.Order, int, error) {
	return r.list(ctx, filter, &userID)
}

func (r *OrderRepository) list(ctx context.Context, f domain.OrderFilter, staffID *int) ([]domain.Order, int, error) {
	const matching = ` FROM orders o WHERE
  ($1::integer IS NULL OR o.custom_id = $1) AND
  ($2::text = '' OR strpos(lower(o.order_code), lower($2)) > 0) AND
  ($3::text = '' OR o.status_id = (SELECT status_id FROM order_statuses WHERE status_code = $3)) AND
  ($4::timestamptz IS NULL OR o.created_at >= $4) AND
  ($5::timestamptz IS NULL OR o.created_at < $5) AND
  ($6::integer IS NULL OR EXISTS (
    SELECT 1 FROM warehouse_staff ws WHERE ws.warehouse_id = o.warehouse_id AND ws.user_id = $6
  ))`
	return queryPage(ctx, GetQuerier(ctx, r.pool), orderColumns, matching, "created_at DESC, order_id DESC", f.Page, func(row pgx.CollectableRow) (domain.Order, error) { return scanOrder(row) },
		f.CustomerID, f.OrderCode, f.StatusCode, f.From, f.Before, staffID)
}

func (r *OrderRepository) UpdateStatus(ctx context.Context, id, statusID int) error {
	return repositoryRowError(GetQuerier(ctx, r.pool).QueryRow(ctx,
		"UPDATE orders SET status_id = $2 WHERE order_id = $1 RETURNING order_id", id, statusID).Scan(&id))
}

func (r *OrderRepository) GetStatusByID(ctx context.Context, id int) (*domain.OrderStatus, error) {
	return r.getStatus(ctx, "status_id", id)
}

func (r *OrderRepository) GetStatusByCode(ctx context.Context, code string) (*domain.OrderStatus, error) {
	return r.getStatus(ctx, "status_code", code)
}

func (r *OrderRepository) getStatus(ctx context.Context, column string, value any) (*domain.OrderStatus, error) {
	var s domain.OrderStatus
	err := GetQuerier(ctx, r.pool).QueryRow(ctx,
		"SELECT status_id, status_code, status_name FROM order_statuses WHERE "+column+" = $1", value).Scan(&s.ID, &s.Code, &s.Name)
	if err != nil {
		return nil, repositoryRowError(err)
	}
	return &s, nil
}

func (r *OrderRepository) CreateItems(ctx context.Context, items []domain.OrderItem) error {
	db := GetQuerier(ctx, r.pool)
	for i := range items {
		item := &items[i]
		err := db.QueryRow(ctx, `INSERT INTO order_items (order_id, product_id, item_name, quantity, unit_price)
   VALUES ($1,$2,$3,$4,$5::bigint::numeric / 100) RETURNING order_item_id`,
			item.OrderID, item.ProductID, item.ItemName, item.Quantity, item.UnitPrice).Scan(&item.ID)
		if err != nil {
			return err
		}
	}
	return nil
}

func (r *OrderRepository) ListItems(ctx context.Context, id int) ([]domain.OrderItem, error) {
	rows, err := GetQuerier(ctx, r.pool).Query(ctx, `SELECT order_item_id, order_id, product_id, item_name, quantity, (unit_price * 100)::bigint
  FROM order_items WHERE order_id = $1 ORDER BY order_item_id`, id)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(row pgx.CollectableRow) (domain.OrderItem, error) {
		var i domain.OrderItem
		err := row.Scan(&i.ID, &i.OrderID, &i.ProductID, &i.ItemName, &i.Quantity, &i.UnitPrice)
		return i, err
	})
}

func (r *OrderRepository) CreateStatusHistory(ctx context.Context, h *domain.OrderStatusHistory) error {
	return GetQuerier(ctx, r.pool).QueryRow(ctx, `INSERT INTO order_status_history (order_id, old_status_id, new_status_id, changed_by)
  VALUES ($1,$2,$3,$4) RETURNING history_id, change_at`, h.OrderID, h.OldStatusID, h.NewStatusID, h.ChangedBy).Scan(&h.ID, &h.ChangeAt)
}

func (r *OrderRepository) ListStatusHistory(ctx context.Context, id int) ([]domain.OrderStatusHistory, error) {
	rows, err := GetQuerier(ctx, r.pool).Query(ctx, `SELECT history_id, order_id, old_status_id, new_status_id, changed_by, change_at
  FROM order_status_history WHERE order_id = $1 ORDER BY change_at, history_id`, id)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(row pgx.CollectableRow) (domain.OrderStatusHistory, error) {
		var h domain.OrderStatusHistory
		err := row.Scan(&h.ID, &h.OrderID, &h.OldStatusID, &h.NewStatusID, &h.ChangedBy, &h.ChangeAt)
		return h, err
	})
}
