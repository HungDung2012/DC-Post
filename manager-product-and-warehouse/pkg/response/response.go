package response

import (
	"errors"
	"net/http"

	"dc-post/manager-product-and-warehouse/pkg/apperror"
	"github.com/gin-gonic/gin"
)

type Meta struct {
	Page       int `json:"page"`
	Limit      int `json:"limit"`
	TotalItems int `json:"total_items"`
	TotalPages int `json:"total_pages"`
}

type successResponse struct {
	Success bool   `json:"success"`
	Message string `json:"message"`
	Data    any    `json:"data"`
	Meta    *Meta  `json:"meta,omitempty"`
}

type errorResponse struct {
	Success bool      `json:"success"`
	Error   errorBody `json:"error"`
}

type errorBody struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	Details any    `json:"details,omitempty"`
}

func JSONSuccess(c *gin.Context, status int, msg string, data any, meta *Meta) {
	c.JSON(status, successResponse{Success: true, Message: msg, Data: data, Meta: meta})
}

func JSONError(c *gin.Context, err error) {
	var appErr *apperror.AppError
	if !errors.As(err, &appErr) || appErr == nil ||
		appErr.HTTPStatus < http.StatusBadRequest || appErr.HTTPStatus >= http.StatusInternalServerError {
		appErr = apperror.ErrInternalServer
	}
	c.JSON(appErr.HTTPStatus, errorResponse{
		Error: errorBody{Code: appErr.Code, Message: appErr.Message, Details: appErr.Details},
	})
}
