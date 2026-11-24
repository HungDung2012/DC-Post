package postgres

import (
	"context"

	"dc-post/manager-product-and-warehouse/internal/domain"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type PaymentRepository struct{ pool *pgxpool.Pool }

var _ domain.PaymentRepository = (*PaymentRepository)(nil)

func NewPaymentRepository(pool *pgxpool.Pool) *PaymentRepository {
	return &PaymentRepository{pool: pool}
}

func (r *PaymentRepository) CreatePayment(ctx context.Context, p *domain.Payment) error {
	return GetQuerier(ctx, r.pool).QueryRow(ctx, `INSERT INTO payments (order_id, amount, payment_method, payment_status, transaction_code, paid_at)
  VALUES ($1,$2::bigint::numeric / 100,$3,$4,$5,$6) RETURNING payment_id`,
		p.OrderID, p.Amount, p.PaymentMethod, p.PaymentStatus, p.TransactionCode, p.PaidAt).Scan(&p.ID)
}

func (r *PaymentRepository) GetPaymentByOrderID(ctx context.Context, id int) (*domain.Payment, error) {
	return r.getPayment(ctx, "order_id", id)
}

func (r *PaymentRepository) GetPaymentByID(ctx context.Context, id int) (*domain.Payment, error) {
	return r.getPayment(ctx, "payment_id", id)
}

func (r *PaymentRepository) getPayment(ctx context.Context, column string, id int) (*domain.Payment, error) {
	var p domain.Payment
	err := GetQuerier(ctx, r.pool).QueryRow(ctx, `SELECT payment_id, order_id, (amount * 100)::bigint, payment_method, payment_status, transaction_code, paid_at
  FROM payments WHERE `+column+" = $1 ORDER BY payment_id LIMIT 1", id).
		Scan(&p.ID, &p.OrderID, &p.Amount, &p.PaymentMethod, &p.PaymentStatus, &p.TransactionCode, &p.PaidAt)
	if err != nil {
		return nil, repositoryRowError(err)
	}
	return &p, nil
}

func (r *PaymentRepository) UpdatePayment(ctx context.Context, p *domain.Payment) error {
	var id int
	return repositoryRowError(GetQuerier(ctx, r.pool).QueryRow(ctx, `UPDATE payments
  SET payment_status = $2, transaction_code = $3, paid_at = $4 WHERE payment_id = $1 RETURNING payment_id`,
		p.ID, p.PaymentStatus, p.TransactionCode, p.PaidAt).Scan(&id))
}

const refundColumns = "refund_id, payment_id, (refund_amount * 100)::bigint, refund_status, created_at"

func scanRefund(row pgx.Row) (domain.Refund, error) {
	var r domain.Refund
	err := row.Scan(&r.ID, &r.PaymentID, &r.RefundAmount, &r.RefundStatus, &r.CreatedAt)
	return r, err
}

func (r *PaymentRepository) CreateRefund(ctx context.Context, f *domain.Refund) error {
	return GetQuerier(ctx, r.pool).QueryRow(ctx, `INSERT INTO refunds (payment_id, refund_amount, refund_status)
  VALUES ($1,$2::bigint::numeric / 100,$3) RETURNING refund_id, created_at`, f.PaymentID, f.RefundAmount, f.RefundStatus).Scan(&f.ID, &f.CreatedAt)
}

func (r *PaymentRepository) GetRefundByID(ctx context.Context, id int) (*domain.Refund, error) {
	f, err := scanRefund(GetQuerier(ctx, r.pool).QueryRow(ctx, "SELECT "+refundColumns+" FROM refunds WHERE refund_id = $1", id))
	if err != nil {
		return nil, repositoryRowError(err)
	}
	return &f, nil
}

func (r *PaymentRepository) ListRefundsByPaymentID(ctx context.Context, id int) ([]domain.Refund, error) {
	rows, err := GetQuerier(ctx, r.pool).Query(ctx, "SELECT "+refundColumns+" FROM refunds WHERE payment_id = $1 ORDER BY refund_id", id)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(row pgx.CollectableRow) (domain.Refund, error) { return scanRefund(row) })
}

func (r *PaymentRepository) UpdateRefund(ctx context.Context, f *domain.Refund) error {
	var id int
	return repositoryRowError(GetQuerier(ctx, r.pool).QueryRow(ctx, `UPDATE refunds SET refund_status = $2 WHERE refund_id = $1 RETURNING refund_id`,
		f.ID, f.RefundStatus).Scan(&id))
}
