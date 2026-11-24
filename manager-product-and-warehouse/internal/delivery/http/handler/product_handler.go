package handler

import (
	"context"
	"net/http"

	"dc-post/manager-product-and-warehouse/internal/delivery/http/middleware"
	"dc-post/manager-product-and-warehouse/internal/dto"
	"dc-post/manager-product-and-warehouse/internal/usecase"
	"github.com/gin-gonic/gin"
)

type ProductService interface {
	Create(context.Context, usecase.Actor, dto.CreateProductReq) (dto.ProductResponse, error)
	List(context.Context, dto.ProductListQuery) ([]dto.ProductResponse, int, error)
	GetByID(context.Context, int) (dto.ProductResponse, error)
	Update(context.Context, usecase.Actor, int, dto.UpdateProductReq) (dto.ProductResponse, error)
}

type ProductHandler struct {
	service ProductService
}

func NewProductHandler(service ProductService) *ProductHandler {
	return &ProductHandler{service: service}
}

func (h *ProductHandler) Create(c *gin.Context) {
	var req dto.CreateProductReq
	if !bindJSON(c, &req) {
		return
	}
	result, err := h.service.Create(c.Request.Context(), middleware.ActorFromContext(c), req)
	respond(c, result, err, http.StatusCreated, "Tạo sản phẩm thành công")
}

func (h *ProductHandler) List(c *gin.Context) {
	var query dto.ProductListQuery
	if !bindQuery(c, &query) {
		return
	}
	result, total, err := h.service.List(c.Request.Context(), query)
	respondPage(c, result, total, query.Page, query.Limit, err)
}

func (h *ProductHandler) GetByID(c *gin.Context) {
	id, ok := pathID(c, "id")
	if !ok {
		return
	}
	result, err := h.service.GetByID(c.Request.Context(), id)
	respond(c, result, err, http.StatusOK, "OK")
}

func (h *ProductHandler) Update(c *gin.Context) {
	id, ok := pathID(c, "id")
	var req dto.UpdateProductReq
	if !ok || !bindJSON(c, &req) {
		return
	}
	result, err := h.service.Update(c.Request.Context(), middleware.ActorFromContext(c), id, req)
	respond(c, result, err, http.StatusOK, "Cập nhật sản phẩm thành công")
}
