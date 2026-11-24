package apperror

import (
	"errors"
	"fmt"
	"testing"
)

func TestNewCarriesApplicationError(t *testing.T) {
	original := New(400, "ERR_INVALID_REQUEST", "quantity is required", "quantity")
	wrapped := fmt.Errorf("create order: %w", original)
	var got *AppError
	if !errors.As(wrapped, &got) {
		t.Fatal("wrapped error does not expose AppError")
	}
	if got.HTTPStatus != 400 || got.Code != "ERR_INVALID_REQUEST" ||
		got.Error() != "quantity is required" || got.Details != "quantity" {
		t.Fatalf("wrong application error: %#v", got)
	}
}

func TestStandardErrorMappings(t *testing.T) {
	for _, tc := range []struct {
		err    *AppError
		status int
		code   string
	}{
		{ErrInvalidRequest, 400, "ERR_INVALID_REQUEST"},
		{ErrInsufficientStock, 400, "ERR_INSUFFICIENT_STOCK"},
		{ErrUnauthorized, 401, "ERR_UNAUTHORIZED"},
		{ErrForbidden, 403, "ERR_FORBIDDEN"},
		{ErrWarehouseAccessDenied, 403, "ERR_WAREHOUSE_ACCESS_DENIED"},
		{ErrResourceNotFound, 404, "ERR_RESOURCE_NOT_FOUND"},
		{ErrDuplicateCode, 409, "ERR_DUPLICATE_CODE"},
		{ErrInvalidStatusTransition, 422, "ERR_INVALID_STATUS_TRANSITION"},
		{ErrBelowReservedStock, 422, "ERR_BELOW_RESERVED_STOCK"},
		{ErrInternalServer, 500, "ERR_INTERNAL_SERVER"},
	} {
		t.Run(tc.code, func(t *testing.T) {
			if tc.err.HTTPStatus != tc.status || tc.err.Code != tc.code || tc.err.Error() == "" {
				t.Fatalf("wrong error mapping: %#v", tc.err)
			}
		})
	}
}
