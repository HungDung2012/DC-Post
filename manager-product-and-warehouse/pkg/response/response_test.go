package response

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http/httptest"
	"reflect"
	"testing"

	"dc-post/manager-product-and-warehouse/pkg/apperror"
	"github.com/gin-gonic/gin"
)

func TestJSONSuccess(t *testing.T) {
	recorder := httptest.NewRecorder()
	ctx, _ := gin.CreateTestContext(recorder)
	JSONSuccess(ctx, 201, "Created", gin.H{"order_id": 42}, &Meta{
		Page: 1, Limit: 20, TotalItems: 125, TotalPages: 7,
	})
	assertResponse(t, recorder, 201, `{
		"success": true, "message": "Created", "data": {"order_id": 42},
		"meta": {"page": 1, "limit": 20, "total_items": 125, "total_pages": 7}
	}`)
}

func TestJSONSuccessWithoutPagination(t *testing.T) {
	recorder := httptest.NewRecorder()
	ctx, _ := gin.CreateTestContext(recorder)
	JSONSuccess(ctx, 200, "OK", nil, nil)
	assertResponse(t, recorder, 200, `{"success": true, "message": "OK", "data": null}`)
}

func TestJSONErrorPreservesWrappedApplicationError(t *testing.T) {
	recorder := httptest.NewRecorder()
	ctx, _ := gin.CreateTestContext(recorder)
	err := apperror.New(400, "ERR_INSUFFICIENT_STOCK", "Insufficient stock", gin.H{"available": 4})
	JSONError(ctx, fmt.Errorf("create order: %w", err))
	assertResponse(t, recorder, 400, `{
		"success": false, "error": {
			"code": "ERR_INSUFFICIENT_STOCK", "message": "Insufficient stock",
			"details": {"available": 4}
		}
	}`)
}

func TestJSONErrorHidesInternalDetails(t *testing.T) {
	for _, err := range []error{
		errors.New("private PostgreSQL error"),
		apperror.New(500, "ERR_PRIVATE", "private PostgreSQL error", "private details"),
	} {
		recorder := httptest.NewRecorder()
		ctx, _ := gin.CreateTestContext(recorder)
		JSONError(ctx, err)
		assertResponse(t, recorder, 500, `{
			"success": false, "error": {
				"code": "ERR_INTERNAL_SERVER", "message": "Internal server error"
			}
		}`)
	}
}

func assertResponse(t *testing.T, recorder *httptest.ResponseRecorder, status int, body string) {
	t.Helper()
	if recorder.Code != status {
		t.Fatalf("status = %d, want %d; body = %s", recorder.Code, status, recorder.Body)
	}
	var got, want any
	if err := json.Unmarshal(recorder.Body.Bytes(), &got); err != nil {
		t.Fatal(err)
	}
	if err := json.Unmarshal([]byte(body), &want); err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("body = %s, want %s", recorder.Body, body)
	}
}
