package postgres

import (
	"context"
	"reflect"
	"strings"
	"testing"

	"github.com/jackc/pgx/v5"
)

type inventoryLockTx struct {
	fakeTx
	productIDs []int32
	query      string
	calls      int
}

func (tx *inventoryLockTx) Query(_ context.Context, sql string, args ...any) (pgx.Rows, error) {
	tx.calls++
	tx.query = sql
	tx.productIDs = args[1].([]int32)
	return emptyInventoryRows{}, nil
}

type emptyInventoryRows struct{ pgx.Rows }

func (emptyInventoryRows) Next() bool { return false }
func (emptyInventoryRows) Close()     {}
func (emptyInventoryRows) Err() error { return nil }

func TestLockInventoryOrdersIDsAndUsesTransactionQuerier(t *testing.T) {
	tx := &inventoryLockTx{}
	manager := TxManager{beginner: fakeBeginner{tx: tx}}
	repo := NewWarehouseRepository(nil)
	ids := []int{9, 2, 9, 1}
	err := manager.WithTransaction(t.Context(), func(ctx context.Context) error {
		rows, err := repo.LockInventoryForUpdate(ctx, 11, ids)
		if len(rows) != 0 {
			t.Fatalf("missing products returned: %+v", rows)
		}
		return err
	})
	if err != nil || tx.calls != 1 || !reflect.DeepEqual(tx.productIDs, []int32{1, 2, 9}) ||
		!reflect.DeepEqual(ids, []int{9, 2, 9, 1}) || !strings.Contains(tx.query, "ORDER BY product_id FOR UPDATE") {
		t.Fatalf("error = %v, calls = %d, IDs = %v, input = %v, SQL = %q", err, tx.calls, tx.productIDs, ids, tx.query)
	}
}

func TestLockInventoryRequiresTransaction(t *testing.T) {
	repo := NewWarehouseRepository(nil)
	if _, err := repo.LockInventoryForUpdate(t.Context(), 11, []int{1}); err == nil {
		t.Fatal("row locks outside a transaction would be released immediately")
	}
}
