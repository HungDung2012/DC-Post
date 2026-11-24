package handler_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	delivery "dc-post/manager-product-and-warehouse/internal/delivery/http"
	"dc-post/manager-product-and-warehouse/internal/delivery/http/handler"
	"dc-post/manager-product-and-warehouse/internal/dto"
	"dc-post/manager-product-and-warehouse/internal/usecase"
	"dc-post/manager-product-and-warehouse/pkg/apperror"
	"github.com/golang-jwt/jwt/v5"
)

const jwtSecret = "handler-tests"

type productMock struct {
	handler.ProductService
	t *testing.T
}

func (m productMock) Create(_ context.Context, actor usecase.Actor, req dto.CreateProductReq) (dto.ProductResponse, error) {
	if actor.UserID != 42 || actor.Role != "STAFF" || req.ProductCode != "P-1" || req.UnitPrice != 12.5 {
		m.t.Fatalf("unexpected create input: %+v %+v", actor, req)
	}
	return dto.ProductResponse{ProductSummary: dto.ProductSummary{ProductID: 5, ProductCode: req.ProductCode}, UnitPrice: req.UnitPrice}, nil
}

func (m productMock) List(_ context.Context, query dto.ProductListQuery) ([]dto.ProductResponse, int, error) {
	if query.Search != "box" || query.Page != 2 || query.Limit != 3 {
		m.t.Fatalf("unexpected list query: %+v", query)
	}
	return []dto.ProductResponse{}, 7, nil
}

func (m productMock) GetByID(_ context.Context, id int) (dto.ProductResponse, error) {
	if id != 99 {
		m.t.Fatalf("unexpected id: %d", id)
	}
	return dto.ProductResponse{}, apperror.ErrResourceNotFound
}

func request(t *testing.T, router http.Handler, method, path, role, body string) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	if role != "" {
		token, err := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
			"user_id": 42, "email": "test@example.test", "role": role, "exp": time.Now().Add(time.Hour).Unix(),
		}).SignedString([]byte(jwtSecret))
		if err != nil {
			t.Fatal(err)
		}
		req.Header.Set("Authorization", "Bearer "+token)
	}
	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, req)
	return rec
}

func expectResponse(t *testing.T, rec *httptest.ResponseRecorder, status int, fragments ...string) {
	t.Helper()
	if rec.Code != status || !json.Valid(rec.Body.Bytes()) {
		t.Fatalf("got %d %s, want %d and JSON", rec.Code, rec.Body, status)
	}
	for _, fragment := range fragments {
		if !strings.Contains(rec.Body.String(), fragment) {
			t.Fatalf("response %s missing %q", rec.Body, fragment)
		}
	}
}

func TestProductRoutes(t *testing.T) {
	router := delivery.NewRouter(jwtSecret, handler.NewProductHandler(productMock{t: t}), handler.NewWarehouseHandler(nil), handler.NewOrderHandler(nil), handler.NewShipmentHandler(nil))
	const body = `{"product_code":"P-1","product_name":"Box","unit_price":12.5}`
	expectResponse(t, request(t, router, "GET", "/health", "", ""), 200, `"status":"ok"`)
	expectResponse(t, request(t, router, "POST", "/api/v1/products", "", body), 401, "ERR_UNAUTHORIZED")
	expectResponse(t, request(t, router, "POST", "/api/v1/products", "CUSTOMER", body), 403, "ERR_FORBIDDEN")
	expectResponse(t, request(t, router, "POST", "/api/v1/products", "STAFF", `{"product_code":"P-1"}`), 400, "ERR_INVALID_REQUEST")
	expectResponse(t, request(t, router, "POST", "/api/v1/products", "STAFF", body+` {}`), 400, "ERR_INVALID_REQUEST")
	expectResponse(t, request(t, router, "POST", "/api/v1/products", "STAFF", body), 201, `"success":true`, `"product_id":5`, `"unit_price":12.5`)
	expectResponse(t, request(t, router, "GET", "/api/v1/products?search=box&page=2&limit=3", "CUSTOMER", ""), 200, `"data":[]`, `"page":2`, `"limit":3`, `"total_items":7`, `"total_pages":3`)
	expectResponse(t, request(t, router, "GET", "/api/v1/products?page=no", "CUSTOMER", ""), 400, "ERR_INVALID_REQUEST")
	expectResponse(t, request(t, router, "GET", "/api/v1/products/0", "CUSTOMER", ""), 400, "ERR_INVALID_REQUEST")
	expectResponse(t, request(t, router, "GET", "/api/v1/products/99", "CUSTOMER", ""), 404, "ERR_RESOURCE_NOT_FOUND")
	expectResponse(t, request(t, router, "PUT", "/api/v1/products/1", "CUSTOMER", ""), 403, "ERR_FORBIDDEN")
}
