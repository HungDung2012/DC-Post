package handler_test

import (
	"context"
	"testing"

	delivery "dc-post/manager-product-and-warehouse/internal/delivery/http"
	"dc-post/manager-product-and-warehouse/internal/delivery/http/handler"
	"dc-post/manager-product-and-warehouse/internal/dto"
	"dc-post/manager-product-and-warehouse/internal/usecase"
)

type orderMock struct {
	handler.OrderService
	t *testing.T
}

func (m orderMock) CreateOrder(_ context.Context, actor usecase.Actor, req dto.CreateOrderReq) (dto.OrderResponse, error) {
	if actor.Role != "CUSTOMER" || actor.UserID != 42 || req.WarehouseID != 9 || len(req.Items) != 1 || req.Items[0].Quantity != 2 {
		m.t.Fatalf("unexpected order input: %+v %+v", actor, req)
	}
	return dto.OrderResponse{OrderSummary: dto.OrderSummary{OrderID: 501, CustomID: actor.UserID}}, nil
}

func (m orderMock) ConfirmPayment(_ context.Context, actor usecase.Actor, id int, req dto.ConfirmPaymentReq) (dto.PaymentResponse, error) {
	if actor.Role != "STAFF" || id != 501 || req.TransactionCode != "TX-1" {
		m.t.Fatalf("unexpected confirmation: %+v %d %+v", actor, id, req)
	}
	return dto.PaymentResponse{PaymentStatus: "PAID"}, nil
}

func TestOrderRoutes(t *testing.T) {
	router := delivery.NewRouter(jwtSecret, handler.NewProductHandler(nil), handler.NewWarehouseHandler(nil), handler.NewOrderHandler(orderMock{t: t}), handler.NewShipmentHandler(nil))
	for _, route := range []struct{ method, path string }{
		{"GET", "/orders"}, {"GET", "/orders/501"}, {"GET", "/orders/501/status-history"}, {"POST", "/orders/501/cancel"},
	} {
		expectResponse(t, request(t, router, route.method, "/api/v1"+route.path, "", ""), 401, "ERR_UNAUTHORIZED")
	}
	for _, route := range []struct{ method, path string }{
		{"PATCH", "/orders/501/status"}, {"POST", "/orders/501/payment/confirm"}, {"PATCH", "/refunds/3/status"},
	} {
		expectResponse(t, request(t, router, route.method, "/api/v1"+route.path, "CUSTOMER", ""), 403, "ERR_FORBIDDEN")
	}
	expectResponse(t, request(t, router, "POST", "/api/v1/orders", "CUSTOMER", `{}`), 400, "ERR_INVALID_REQUEST")
	expectResponse(t, request(t, router, "POST", "/api/v1/orders", "CUSTOMER", `{"warehouse_id":9,"payment_method":"COD","items":[{"product_id":8,"quantity":0}]}`), 400, "ERR_INVALID_REQUEST")
	expectResponse(t, request(t, router, "POST", "/api/v1/orders", "CUSTOMER", `{"warehouse_id":9,"payment_method":"COD","items":[{"product_id":8,"quantity":2}]}`), 201, `"order_id":501`, `"custom_id":42`)
	expectResponse(t, request(t, router, "POST", "/api/v1/orders/501/payment/confirm", "STAFF", `{"transaction_code":"TX-1"}`), 200, `"payment_status":"PAID"`)
}
