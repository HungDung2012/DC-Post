package postgres

import (
	"context"
	"errors"

	"dc-post/manager-product-and-warehouse/internal/domain"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type ShipmentRepository struct{ pool *pgxpool.Pool }

var _ domain.ShipmentRepository = (*ShipmentRepository)(nil)

func NewShipmentRepository(pool *pgxpool.Pool) *ShipmentRepository {
	return &ShipmentRepository{pool: pool}
}

const shipmentColumns = "shipment_id, order_id, tracking_code, shipper_id, status_id, current_warehouse_id, (cod_amount * 100)::bigint, delivered_at"

func (r *ShipmentRepository) Create(ctx context.Context, s *domain.Shipment) error {
	err := GetQuerier(ctx, r.pool).QueryRow(ctx, `INSERT INTO shipments (order_id, tracking_code, shipper_id, status_id, current_warehouse_id, cod_amount, delivered_at)
  VALUES ($1,$2,$3,$4,$5,$6::bigint::numeric / 100,$7) RETURNING shipment_id`,
		s.OrderID, s.TrackingCode, s.ShipperID, s.StatusID, s.CurrentWarehouseID, s.CODAmount, s.DeliveredAt).Scan(&s.ID)
	return businessCodeError(err, "shipments_tracking_code_key")
}

func (r *ShipmentRepository) GetByID(ctx context.Context, id int) (*domain.Shipment, error) {
	return r.getShipment(ctx, "shipment_id = $1", id)
}

func (r *ShipmentRepository) GetByIDForUpdate(ctx context.Context, id int) (*domain.Shipment, error) {
	if _, ok := ctx.Value(txContextKey{}).(pgx.Tx); !ok {
		return nil, errors.New("locking a shipment requires a transaction")
	}
	return r.getShipment(ctx, "shipment_id = $1 FOR UPDATE", id)
}

func (r *ShipmentRepository) GetByOrderID(ctx context.Context, id int) (*domain.Shipment, error) {
	return r.getShipment(ctx, "order_id = $1 ORDER BY shipment_id LIMIT 1", id)
}

func (r *ShipmentRepository) GetByTrackingCode(ctx context.Context, code string) (*domain.Shipment, error) {
	return r.getShipment(ctx, "tracking_code = $1", code)
}

func (r *ShipmentRepository) getShipment(ctx context.Context, where string, value any) (*domain.Shipment, error) {
	var s domain.Shipment
	err := GetQuerier(ctx, r.pool).QueryRow(ctx, "SELECT "+shipmentColumns+" FROM shipments WHERE "+where, value).
		Scan(&s.ID, &s.OrderID, &s.TrackingCode, &s.ShipperID, &s.StatusID, &s.CurrentWarehouseID, &s.CODAmount, &s.DeliveredAt)
	if err != nil {
		return nil, repositoryRowError(err)
	}
	return &s, nil
}

func (r *ShipmentRepository) Update(ctx context.Context, s *domain.Shipment) error {
	var id int
	return repositoryRowError(GetQuerier(ctx, r.pool).QueryRow(ctx, `UPDATE shipments
  SET shipper_id = $2, status_id = $3, current_warehouse_id = $4, cod_amount = $5::bigint::numeric / 100, delivered_at = $6
  WHERE shipment_id = $1 RETURNING shipment_id`, s.ID, s.ShipperID, s.StatusID, s.CurrentWarehouseID, s.CODAmount, s.DeliveredAt).Scan(&id))
}

func (r *ShipmentRepository) GetStatusByID(ctx context.Context, id int) (*domain.ShipmentStatus, error) {
	return r.getStatus(ctx, "status_id", id)
}

func (r *ShipmentRepository) GetStatusByCode(ctx context.Context, code string) (*domain.ShipmentStatus, error) {
	return r.getStatus(ctx, "status_code", code)
}

func (r *ShipmentRepository) getStatus(ctx context.Context, column string, value any) (*domain.ShipmentStatus, error) {
	var s domain.ShipmentStatus
	err := GetQuerier(ctx, r.pool).QueryRow(ctx, "SELECT status_id, status_code, status_name FROM shipment_statuses WHERE "+column+" = $1", value).
		Scan(&s.ID, &s.Code, &s.Name)
	if err != nil {
		return nil, repositoryRowError(err)
	}
	return &s, nil
}

func (r *ShipmentRepository) CreateItems(ctx context.Context, items []domain.ShipmentItem) error {
	db := GetQuerier(ctx, r.pool)
	for i := range items {
		item := &items[i]
		err := db.QueryRow(ctx, `INSERT INTO shipment_items (shipment_id, order_item_id, quantity)
   VALUES ($1,$2,$3) RETURNING shipment_item_id`, item.ShipmentID, item.OrderItemID, item.Quantity).Scan(&item.ID)
		if err != nil {
			return err
		}
	}
	return nil
}

func (r *ShipmentRepository) ListItems(ctx context.Context, id int) ([]domain.ShipmentItem, error) {
	rows, err := GetQuerier(ctx, r.pool).Query(ctx, `SELECT shipment_item_id, shipment_id, order_item_id, quantity
  FROM shipment_items WHERE shipment_id = $1 ORDER BY shipment_item_id`, id)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(row pgx.CollectableRow) (domain.ShipmentItem, error) {
		var i domain.ShipmentItem
		err := row.Scan(&i.ID, &i.ShipmentID, &i.OrderItemID, &i.Quantity)
		return i, err
	})
}

func (r *ShipmentRepository) CreateTrackingEvent(ctx context.Context, e *domain.ShipmentTrackingEvent) error {
	return GetQuerier(ctx, r.pool).QueryRow(ctx, `INSERT INTO shipment_tracking_events (shipment_id, status_id, warehouse_id, scanned_by, from_warehouse_id)
  VALUES ($1,$2,$3,$4,$5) RETURNING event_id, scanned_at`, e.ShipmentID, e.StatusID, e.WarehouseID, e.ScannedBy, e.FromWarehouseID).Scan(&e.ID, &e.ScannedAt)
}

func (r *ShipmentRepository) ListTrackingEvents(ctx context.Context, id int) ([]domain.ShipmentTrackingEvent, error) {
	rows, err := GetQuerier(ctx, r.pool).Query(ctx, `SELECT event_id, shipment_id, status_id, warehouse_id, scanned_by, from_warehouse_id, scanned_at
  FROM shipment_tracking_events WHERE shipment_id = $1 ORDER BY scanned_at DESC, event_id DESC`, id)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(row pgx.CollectableRow) (domain.ShipmentTrackingEvent, error) {
		var e domain.ShipmentTrackingEvent
		err := row.Scan(&e.ID, &e.ShipmentID, &e.StatusID, &e.WarehouseID, &e.ScannedBy, &e.FromWarehouseID, &e.ScannedAt)
		return e, err
	})
}
