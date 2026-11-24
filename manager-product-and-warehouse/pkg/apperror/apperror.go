package apperror

import "net/http"

// AppError holds the HTTP status and the error information available to clients.
type AppError struct {
	HTTPStatus int
	Code       string
	Message    string
	Details    any
}

func New(httpStatus int, code, message string, details any) *AppError {
	return &AppError{HTTPStatus: httpStatus, Code: code, Message: message, Details: details}
}

func (e *AppError) Error() string {
	return e.Message
}

var (
	ErrInvalidRequest          = New(http.StatusBadRequest, "ERR_INVALID_REQUEST", "Invalid request", nil)
	ErrInsufficientStock       = New(http.StatusBadRequest, "ERR_INSUFFICIENT_STOCK", "Insufficient available stock", nil)
	ErrUnauthorized            = New(http.StatusUnauthorized, "ERR_UNAUTHORIZED", "Authentication required", nil)
	ErrForbidden               = New(http.StatusForbidden, "ERR_FORBIDDEN", "Access forbidden", nil)
	ErrWarehouseAccessDenied   = New(http.StatusForbidden, "ERR_WAREHOUSE_ACCESS_DENIED", "Warehouse access denied", nil)
	ErrResourceNotFound        = New(http.StatusNotFound, "ERR_RESOURCE_NOT_FOUND", "Resource not found", nil)
	ErrDuplicateCode           = New(http.StatusConflict, "ERR_DUPLICATE_CODE", "Duplicate code", nil)
	ErrInvalidStatusTransition = New(http.StatusUnprocessableEntity, "ERR_INVALID_STATUS_TRANSITION", "Invalid status transition", nil)
	ErrBelowReservedStock      = New(http.StatusUnprocessableEntity, "ERR_BELOW_RESERVED_STOCK", "Stock cannot be lower than the reserved quantity", nil)
	ErrInternalServer          = New(http.StatusInternalServerError, "ERR_INTERNAL_SERVER", "Internal server error", nil)
)
