package handler

import (
	"encoding/json"
	"io"
	"net/http"
	"strconv"

	"dc-post/manager-product-and-warehouse/pkg/apperror"
	"dc-post/manager-product-and-warehouse/pkg/response"
	"github.com/gin-gonic/gin"
	"github.com/gin-gonic/gin/binding"
)

func invalidRequest(c *gin.Context) {
	response.JSONError(c, apperror.ErrInvalidRequest)
}

func bindJSON[T any](c *gin.Context, payload *T) bool {
	const maxJSONBody = 1 << 20
	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxJSONBody)
	decoder := json.NewDecoder(c.Request.Body)
	var value *T
	if err := decoder.Decode(&value); err != nil || value == nil {
		invalidRequest(c)
		return false
	}
	if decoder.Decode(new(json.RawMessage)) != io.EOF || binding.Validator.ValidateStruct(value) != nil {
		invalidRequest(c)
		return false
	}
	*payload = *value
	return true
}

func bindQuery(c *gin.Context, query any) bool {
	if c.ShouldBindQuery(query) != nil {
		invalidRequest(c)
		return false
	}
	return true
}

func pathID(c *gin.Context, name string) (int, bool) {
	id, err := strconv.ParseUint(c.Param(name), 10, 31)
	if err != nil || id == 0 {
		invalidRequest(c)
		return 0, false
	}
	return int(id), true
}

func respond(c *gin.Context, data any, err error, status int, message string) {
	if err != nil {
		response.JSONError(c, err)
		return
	}
	response.JSONSuccess(c, status, message, data, nil)
}

func respondPage[T any](c *gin.Context, data []T, total, page, limit int, err error) {
	if err != nil {
		response.JSONError(c, err)
		return
	}
	// The defaults match the usecases' pagination.
	if page == 0 {
		page = 1
	}
	if limit == 0 {
		limit = 20
	}
	totalPages := total / limit
	if total%limit != 0 {
		totalPages++
	}
	if data == nil {
		data = []T{}
	}
	response.JSONSuccess(c, http.StatusOK, "OK", data, &response.Meta{
		Page: page, Limit: limit, TotalItems: total, TotalPages: totalPages,
	})
}
