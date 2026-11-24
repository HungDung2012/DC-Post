package postgres

import (
	"context"
	"errors"
	"fmt"
	"time"

	"dc-post/manager-product-and-warehouse/internal/domain"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

// DBTX is the query interface shared by the pool and a transaction.
type DBTX interface {
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

type txContextKey struct{}

type txBeginner interface {
	Begin(ctx context.Context) (pgx.Tx, error)
}

type TxManager struct {
	beginner txBeginner
}

var (
	_ domain.TxManager = (*TxManager)(nil)
	_ DBTX             = (*pgxpool.Pool)(nil)
	_ DBTX             = (pgx.Tx)(nil)
)

func NewTxManager(pool *pgxpool.Pool) *TxManager {
	return &TxManager{beginner: pool}
}

// NewPool creates a pool configured by dsn. The caller owns the pool and can
// use Ping to verify connectivity.
func NewPool(ctx context.Context, dsn string) (*pgxpool.Pool, error) {
	return pgxpool.New(ctx, dsn)
}

// GetQuerier returns the transaction in ctx, or fallback outside a transaction.
func GetQuerier(ctx context.Context, fallback DBTX) DBTX {
	if tx, ok := ctx.Value(txContextKey{}).(pgx.Tx); ok {
		return tx
	}
	return fallback
}

func (m *TxManager) WithTransaction(ctx context.Context, fn func(txCtx context.Context) error) (err error) {
	tx, err := m.beginner.Begin(ctx)
	if err != nil {
		return fmt.Errorf("begin transaction: %w", err)
	}

	committed := false
	defer func() {
		if committed {
			return
		}
		// Release the connection even when the request context was cancelled.
		rollbackCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), 5*time.Second)
		defer cancel()
		if rollbackErr := tx.Rollback(rollbackCtx); rollbackErr != nil && !errors.Is(rollbackErr, pgx.ErrTxClosed) {
			err = errors.Join(err, fmt.Errorf("rollback transaction: %w", rollbackErr))
		}
	}()

	txCtx := context.WithValue(ctx, txContextKey{}, tx)
	if err := fn(txCtx); err != nil {
		return err
	}
	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("commit transaction: %w", err)
	}
	committed = true
	return nil
}
