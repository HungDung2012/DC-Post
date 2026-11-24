package postgres

import (
	"context"
	"errors"
	"testing"

	"github.com/jackc/pgx/v5"
)

type fakeBeginner struct {
	tx  pgx.Tx
	err error
}

func (b fakeBeginner) Begin(context.Context) (pgx.Tx, error) {
	return b.tx, b.err
}

type fakeTx struct {
	pgx.Tx
	commits        int
	rollbacks      int
	commitErr      error
	rollbackErr    error
	rollbackCtxErr error
}

func (tx *fakeTx) Commit(context.Context) error {
	tx.commits++
	return tx.commitErr
}

func (tx *fakeTx) Rollback(ctx context.Context) error {
	tx.rollbacks++
	tx.rollbackCtxErr = ctx.Err()
	return tx.rollbackErr
}

func TestGetQuerierReturnsFallbackOutsideTransaction(t *testing.T) {
	fallback := &fakeTx{}
	if got := GetQuerier(t.Context(), fallback); got != fallback {
		t.Fatalf("GetQuerier() returned %T, want fallback", got)
	}
}

func TestWithTransactionCommitsAndPropagatesQuerier(t *testing.T) {
	tx := &fakeTx{}
	manager := TxManager{beginner: fakeBeginner{tx: tx}}
	fallback := &fakeTx{}
	ctx := t.Context()

	err := manager.WithTransaction(ctx, func(txCtx context.Context) error {
		child, cancel := context.WithCancel(txCtx)
		defer cancel()
		if got := GetQuerier(child, fallback); got != tx {
			t.Errorf("GetQuerier() returned %T, want transaction", got)
		}
		if tx.commits != 0 || tx.rollbacks != 0 {
			t.Error("transaction finalized before callback completed")
		}
		return nil
	})

	if err != nil || tx.commits != 1 || tx.rollbacks != 0 {
		t.Fatalf("got error %v, commits %d, rollbacks %d", err, tx.commits, tx.rollbacks)
	}
	if got := GetQuerier(ctx, fallback); got != fallback {
		t.Fatal("transaction leaked to the caller's context")
	}
}

func TestWithTransactionRollsBackCallbackErrorAfterCancellation(t *testing.T) {
	tx := &fakeTx{}
	manager := TxManager{beginner: fakeBeginner{tx: tx}}
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	want := errors.New("callback failed")

	err := manager.WithTransaction(ctx, func(context.Context) error {
		cancel()
		return want
	})

	if !errors.Is(err, want) || tx.commits != 0 || tx.rollbacks != 1 {
		t.Fatalf("got error %v, commits %d, rollbacks %d", err, tx.commits, tx.rollbacks)
	}
	if tx.rollbackCtxErr != nil {
		t.Fatalf("rollback was given a cancelled context: %v", tx.rollbackCtxErr)
	}
}

func TestWithTransactionRollsBackPanic(t *testing.T) {
	tx := &fakeTx{}
	manager := TxManager{beginner: fakeBeginner{tx: tx}}
	want := errors.New("callback panicked")

	defer func() {
		if got := recover(); got != want {
			t.Errorf("recovered %v, want %v", got, want)
		}
		if tx.commits != 0 || tx.rollbacks != 1 {
			t.Errorf("got commits %d and rollbacks %d", tx.commits, tx.rollbacks)
		}
	}()
	_ = manager.WithTransaction(t.Context(), func(context.Context) error {
		panic(want)
	})
}

func TestWithTransactionReturnsBeginError(t *testing.T) {
	want := errors.New("begin failed")
	manager := TxManager{beginner: fakeBeginner{err: want}}
	err := manager.WithTransaction(t.Context(), func(context.Context) error {
		t.Error("callback ran after begin failed")
		return nil
	})
	if !errors.Is(err, want) {
		t.Fatalf("got %v, want %v", err, want)
	}
}

func TestWithTransactionReturnsCommitError(t *testing.T) {
	want := errors.New("commit failed")
	tx := &fakeTx{commitErr: want, rollbackErr: pgx.ErrTxClosed}
	manager := TxManager{beginner: fakeBeginner{tx: tx}}
	err := manager.WithTransaction(t.Context(), func(context.Context) error {
		return nil
	})
	if !errors.Is(err, want) || errors.Is(err, pgx.ErrTxClosed) || tx.commits != 1 {
		t.Fatalf("got error %v and %d commits", err, tx.commits)
	}
}

func TestWithTransactionPreservesCallbackAndRollbackErrors(t *testing.T) {
	callbackErr := errors.New("callback failed")
	rollbackErr := errors.New("rollback failed")
	tx := &fakeTx{rollbackErr: rollbackErr}
	manager := TxManager{beginner: fakeBeginner{tx: tx}}

	err := manager.WithTransaction(t.Context(), func(context.Context) error {
		return callbackErr
	})
	if !errors.Is(err, callbackErr) || !errors.Is(err, rollbackErr) {
		t.Fatalf("got %v, want both callback and rollback errors", err)
	}
}

func TestNewPoolRejectsMalformedDSN(t *testing.T) {
	pool, err := NewPool(t.Context(), "postgres://localhost:invalid/db")
	if pool != nil {
		pool.Close()
	}
	if err == nil || pool != nil {
		t.Fatal("NewPool accepted malformed DSN")
	}
}

func TestNewPoolUsesDSN(t *testing.T) {
	pool, err := NewPool(t.Context(), "postgres://test@localhost/part2?pool_max_conns=3")
	if err != nil {
		t.Fatal(err)
	}
	defer pool.Close()

	if cfg := pool.Config(); cfg.ConnConfig.Database != "part2" || cfg.MaxConns != 3 {
		t.Fatal("pool configuration does not reflect the DSN")
	}
	if manager := NewTxManager(pool); manager.beginner != pool {
		t.Fatal("transaction manager is not wired to the supplied pool")
	}
}
