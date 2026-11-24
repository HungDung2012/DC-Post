package postgres

import (
	"context"
	"errors"
	"fmt"

	"dc-post/manager-product-and-warehouse/internal/domain"
	"dc-post/manager-product-and-warehouse/pkg/apperror"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

type ProductRepository struct {
	pool *pgxpool.Pool
}

var _ domain.ProductRepository = (*ProductRepository)(nil)

func NewProductRepository(pool *pgxpool.Pool) *ProductRepository {
	return &ProductRepository{pool: pool}
}

const productColumns = "product_id, product_code, product_name, (unit_price * 100)::bigint AS unit_price_hundredths"

func scanProduct(row pgx.CollectableRow) (domain.Product, error) {
	var p domain.Product
	err := row.Scan(&p.ID, &p.Code, &p.Name, &p.UnitPrice)
	return p, err
}

func (r *ProductRepository) Create(ctx context.Context, product *domain.Product) error {
	err := GetQuerier(ctx, r.pool).QueryRow(ctx, `
		INSERT INTO products (product_code, product_name, unit_price)
		VALUES ($1, $2, $3::bigint::numeric / 100)
		RETURNING product_id`, product.Code, product.Name, product.UnitPrice).Scan(&product.ID)
	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) && pgErr.Code == "23505" && pgErr.ConstraintName == "products_product_code_key" {
		return apperror.ErrDuplicateCode
	}
	return err
}

func (r *ProductRepository) GetByID(ctx context.Context, id int) (*domain.Product, error) {
	var p domain.Product
	err := GetQuerier(ctx, r.pool).QueryRow(ctx,
		"SELECT "+productColumns+" FROM products WHERE product_id = $1", id).
		Scan(&p.ID, &p.Code, &p.Name, &p.UnitPrice)
	if err != nil {
		return nil, repositoryRowError(err)
	}
	return &p, nil
}

func (r *ProductRepository) List(ctx context.Context, filter domain.ProductFilter) ([]domain.Product, int, error) {
	const matching = ` FROM products WHERE
		strpos(lower(product_code), lower($1::text)) > 0 OR
		strpos(lower(product_name), lower($1::text)) > 0`
	return queryPage(ctx, GetQuerier(ctx, r.pool), productColumns, matching, "product_id", filter.Page, scanProduct, filter.Search)
}

func (r *ProductRepository) Update(ctx context.Context, product *domain.Product) error {
	err := GetQuerier(ctx, r.pool).QueryRow(ctx, `
		UPDATE products SET product_name = $2, unit_price = $3::bigint::numeric / 100
		WHERE product_id = $1 RETURNING product_code`,
		product.ID, product.Name, product.UnitPrice).Scan(&product.Code)
	return repositoryRowError(err)
}

func repositoryRowError(err error) error {
	if errors.Is(err, pgx.ErrNoRows) {
		return apperror.ErrResourceNotFound
	}
	return err
}

// queryPage reads the total and page from one snapshot. The left join preserves
// the total when the offset is beyond the last matching row.
func queryPage[T any](ctx context.Context, db DBTX, columns, matching, order string, page domain.Page, scan pgx.RowToFunc[T], args ...any) ([]T, int, error) {
	query := "WITH matched AS NOT MATERIALIZED (SELECT " + columns + matching + ")," +
		" paged AS (SELECT * FROM matched ORDER BY " + order +
		fmt.Sprintf(" LIMIT $%d OFFSET $%d)", len(args)+1, len(args)+2) +
		" SELECT totals.total, paged.* FROM (SELECT count(*) AS total FROM matched) totals" +
		" LEFT JOIN paged ON true ORDER BY " + order
	rows, err := db.Query(ctx, query, append(args, page.Limit, page.Offset)...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var total int
	items := make([]T, 0)
	for rows.Next() {
		// Each projection starts with a non-null ID. A null ID marks an empty page.
		raw := rows.RawValues()
		if raw[1] == nil {
			dest := make([]any, len(raw))
			dest[0] = &total
			if err := rows.Scan(dest...); err != nil {
				return nil, 0, err
			}
			continue
		}
		item, err := scan(pageRow{Rows: rows, total: &total})
		if err != nil {
			return nil, 0, err
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	return items, total, nil
}

// pageRow consumes the total column before the normal entity projection.
type pageRow struct {
	pgx.Rows
	total *int
}

func (r pageRow) Scan(dest ...any) error {
	return r.Rows.Scan(append([]any{r.total}, dest...)...)
}
