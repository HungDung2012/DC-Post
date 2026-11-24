package handler

import (
	"context"
	"net/http"

	"dc-post/manager-product-and-warehouse/internal/delivery/http/middleware"
	"dc-post/manager-product-and-warehouse/internal/dto"
	"dc-post/manager-product-and-warehouse/internal/usecase"
	"github.com/gin-gonic/gin"
)

type WarehouseService interface {
	CreateWarehouse(context.Context, usecase.Actor, dto.WarehouseReq) (dto.WarehouseResponse, error)
	ListWarehouses(context.Context, usecase.Actor, dto.WarehouseListQuery) ([]dto.WarehouseResponse, int, error)
	UpdateWarehouse(context.Context, usecase.Actor, int, dto.WarehouseReq) (dto.WarehouseResponse, error)
	AssignStaff(context.Context, usecase.Actor, int, dto.AssignStaffReq) error
	RemoveStaff(context.Context, usecase.Actor, int, int) error
	GetWarehouseInventory(context.Context, usecase.Actor, int, dto.InventoryListQuery) ([]dto.InventoryResponse, int, error)
	ImportStock(context.Context, usecase.Actor, dto.ImportStockReq) (dto.StockChangeResponse, error)
	ExportManualStock(context.Context, usecase.Actor, dto.ExportStockReq) (dto.StockChangeResponse, error)
	AdjustStock(context.Context, usecase.Actor, dto.AdjustStockReq) (dto.StockChangeResponse, error)
	ListInventoryTransactions(context.Context, usecase.Actor, dto.InventoryTxFilter) ([]dto.InventoryTransactionResponse, int, error)
}

type WarehouseHandler struct {
	service WarehouseService
}

func NewWarehouseHandler(service WarehouseService) *WarehouseHandler {
	return &WarehouseHandler{service: service}
}

func (h *WarehouseHandler) CreateWarehouse(c *gin.Context) {
	var req dto.WarehouseReq
	if !bindJSON(c, &req) {
		return
	}
	result, err := h.service.CreateWarehouse(c.Request.Context(), middleware.ActorFromContext(c), req)
	respond(c, result, err, http.StatusCreated, "Tạo kho thành công")
}

func (h *WarehouseHandler) ListWarehouses(c *gin.Context) {
	var query dto.WarehouseListQuery
	if !bindQuery(c, &query) {
		return
	}
	result, total, err := h.service.ListWarehouses(c.Request.Context(), middleware.ActorFromContext(c), query)
	respondPage(c, result, total, query.Page, query.Limit, err)
}

func (h *WarehouseHandler) UpdateWarehouse(c *gin.Context) {
	id, ok := pathID(c, "id")
	if !ok {
		return
	}
	var req dto.WarehouseReq
	if !bindJSON(c, &req) {
		return
	}
	result, err := h.service.UpdateWarehouse(c.Request.Context(), middleware.ActorFromContext(c), id, req)
	respond(c, result, err, http.StatusOK, "Cập nhật kho thành công")
}

func (h *WarehouseHandler) AssignStaff(c *gin.Context) {
	id, ok := pathID(c, "id")
	if !ok {
		return
	}
	var req dto.AssignStaffReq
	if !bindJSON(c, &req) {
		return
	}
	err := h.service.AssignStaff(c.Request.Context(), middleware.ActorFromContext(c), id, req)
	respond(c, nil, err, http.StatusCreated, "Phân công nhân viên thành công")
}

func (h *WarehouseHandler) RemoveStaff(c *gin.Context) {
	id, ok := pathID(c, "id")
	if !ok {
		return
	}
	userID, ok := pathID(c, "user_id")
	if !ok {
		return
	}
	err := h.service.RemoveStaff(c.Request.Context(), middleware.ActorFromContext(c), id, userID)
	respond(c, nil, err, http.StatusOK, "Gỡ nhân viên khỏi kho thành công")
}

func (h *WarehouseHandler) GetWarehouseInventory(c *gin.Context) {
	id, ok := pathID(c, "warehouse_id")
	if !ok {
		return
	}
	var query dto.InventoryListQuery
	if !bindQuery(c, &query) {
		return
	}
	result, total, err := h.service.GetWarehouseInventory(c.Request.Context(), middleware.ActorFromContext(c), id, query)
	respondPage(c, result, total, query.Page, query.Limit, err)
}

func (h *WarehouseHandler) ImportStock(c *gin.Context) {
	var req dto.ImportStockReq
	if !bindJSON(c, &req) {
		return
	}
	result, err := h.service.ImportStock(c.Request.Context(), middleware.ActorFromContext(c), req)
	respond(c, result, err, http.StatusOK, "Nhập kho thành công")
}

func (h *WarehouseHandler) ExportManualStock(c *gin.Context) {
	var req dto.ExportStockReq
	if !bindJSON(c, &req) {
		return
	}
	result, err := h.service.ExportManualStock(c.Request.Context(), middleware.ActorFromContext(c), req)
	respond(c, result, err, http.StatusOK, "Xuất kho thành công")
}

func (h *WarehouseHandler) AdjustStock(c *gin.Context) {
	var req dto.AdjustStockReq
	if !bindJSON(c, &req) {
		return
	}
	result, err := h.service.AdjustStock(c.Request.Context(), middleware.ActorFromContext(c), req)
	respond(c, result, err, http.StatusOK, "Kiểm kê kho thành công")
}

func (h *WarehouseHandler) ListInventoryTransactions(c *gin.Context) {
	var query dto.InventoryTxFilter
	if !bindQuery(c, &query) {
		return
	}
	result, total, err := h.service.ListInventoryTransactions(c.Request.Context(), middleware.ActorFromContext(c), query)
	respondPage(c, result, total, query.Page, query.Limit, err)
}
