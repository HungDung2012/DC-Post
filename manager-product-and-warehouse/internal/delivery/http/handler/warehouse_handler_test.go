package handler_test

import (
	"context"
	"testing"

	delivery "dc-post/manager-product-and-warehouse/internal/delivery/http"
	"dc-post/manager-product-and-warehouse/internal/delivery/http/handler"
	"dc-post/manager-product-and-warehouse/internal/dto"
	"dc-post/manager-product-and-warehouse/internal/usecase"
	"dc-post/manager-product-and-warehouse/pkg/apperror"
)

type warehouseMock struct {
	handler.WarehouseService
	t *testing.T
}

func (m warehouseMock) AdjustStock(_ context.Context, actor usecase.Actor, req dto.AdjustStockReq) (dto.StockChangeResponse, error) {
	if actor.UserID != 42 || req.WarehouseID != 3 || req.ProductID != 8 || req.ActualQuantity != 0 {
		m.t.Fatalf("unexpected adjustment: %+v %+v", actor, req)
	}
	return dto.StockChangeResponse{WarehouseID: req.WarehouseID, Transactions: []dto.StockChange{{NewStockQuantity: 0}}}, nil
}

func (m warehouseMock) GetWarehouseInventory(_ context.Context, actor usecase.Actor, id int, query dto.InventoryListQuery) ([]dto.InventoryResponse, int, error) {
	if actor.Role != "STAFF" || id != 3 || !query.LowStock {
		m.t.Fatalf("unexpected inventory input: %+v %d %+v", actor, id, query)
	}
	return nil, 0, apperror.ErrWarehouseAccessDenied
}

func (m warehouseMock) RemoveStaff(_ context.Context, actor usecase.Actor, id, userID int) error {
	if actor.Role != "ADMIN" || id != 3 || userID != 19 {
		m.t.Fatalf("unexpected removal: %+v %d %d", actor, id, userID)
	}
	return nil
}

func TestWarehouseRoutes(t *testing.T) {
	router := delivery.NewRouter(jwtSecret, handler.NewProductHandler(nil), handler.NewWarehouseHandler(warehouseMock{t: t}), handler.NewOrderHandler(nil), handler.NewShipmentHandler(nil))
	for _, route := range []struct{ method, path string }{
		{"POST", "/warehouses"}, {"PUT", "/warehouses/3"}, {"POST", "/warehouses/3/staff"}, {"DELETE", "/warehouses/3/staff/19"},
	} {
		expectResponse(t, request(t, router, route.method, "/api/v1"+route.path, "STAFF", ""), 403, "ERR_FORBIDDEN")
	}
	for _, route := range []struct{ method, path string }{
		{"GET", "/warehouses"}, {"GET", "/warehouses/3/inventory"}, {"POST", "/inventory/import"}, {"POST", "/inventory/export"}, {"POST", "/inventory/adjust"}, {"GET", "/inventory/transactions"},
	} {
		expectResponse(t, request(t, router, route.method, "/api/v1"+route.path, "CUSTOMER", ""), 403, "ERR_FORBIDDEN")
	}
	expectResponse(t, request(t, router, "POST", "/api/v1/inventory/adjust", "STAFF", `{"warehouse_id":3,"product_id":8}`), 400, "ERR_INVALID_REQUEST")
	expectResponse(t, request(t, router, "POST", "/api/v1/inventory/adjust", "STAFF", `{"warehouse_id":3,"product_id":8,"actual_quantity":0}`), 200, `"new_stock_quantity":0`)
	expectResponse(t, request(t, router, "GET", "/api/v1/warehouses/3/inventory?low_stock=true", "STAFF", ""), 403, "ERR_WAREHOUSE_ACCESS_DENIED")
	expectResponse(t, request(t, router, "DELETE", "/api/v1/warehouses/3/staff/19", "ADMIN", ""), 200, `"success":true`)
}
