package handler_test

import (
	"context"
	"testing"

	delivery "dc-post/manager-product-and-warehouse/internal/delivery/http"
	"dc-post/manager-product-and-warehouse/internal/delivery/http/handler"
	"dc-post/manager-product-and-warehouse/internal/dto"
	"dc-post/manager-product-and-warehouse/internal/usecase"
)

type shipmentMock struct {
	handler.ShipmentService
	t *testing.T
}

func (m shipmentMock) GetShipmentByTrackingCode(_ context.Context, actor usecase.Actor, code string) (dto.ShipmentTrackingResponse, error) {
	if actor.Role != "CUSTOMER" || code != "TRK-123" {
		m.t.Fatalf("unexpected tracking input: %+v %s", actor, code)
	}
	return dto.ShipmentTrackingResponse{ShipmentID: 701, TrackingCode: code}, nil
}

func (m shipmentMock) CreateShipment(_ context.Context, actor usecase.Actor, req dto.CreateShipmentReq) (dto.ShipmentResponse, error) {
	if actor.Role != "STAFF" || req.OrderID != 501 {
		m.t.Fatalf("unexpected shipment input: %+v %+v", actor, req)
	}
	return dto.ShipmentResponse{ShipmentID: 701, OrderID: req.OrderID}, nil
}

func TestShipmentRoutes(t *testing.T) {
	router := delivery.NewRouter(jwtSecret, handler.NewProductHandler(nil), handler.NewWarehouseHandler(nil), handler.NewOrderHandler(nil), handler.NewShipmentHandler(shipmentMock{t: t}))
	expectResponse(t, request(t, router, "POST", "/api/v1/shipments", "CUSTOMER", ""), 403, "ERR_FORBIDDEN")
	expectResponse(t, request(t, router, "POST", "/api/v1/shipments/701/tracking-events", "CUSTOMER", ""), 403, "ERR_FORBIDDEN")
	expectResponse(t, request(t, router, "POST", "/api/v1/shipments/701/tracking-events", "STAFF", `{}`), 400, "ERR_INVALID_REQUEST")
	expectResponse(t, request(t, router, "POST", "/api/v1/shipments", "STAFF", `{"order_id":501}`), 201, `"shipment_id":701`)
	expectResponse(t, request(t, router, "GET", "/api/v1/shipments/tracking/TRK-123", "CUSTOMER", ""), 200, `"tracking_code":"TRK-123"`)
}
