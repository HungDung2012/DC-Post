package handler

import (
	"context"
	"net/http"
	"strings"

	"dc-post/manager-product-and-warehouse/internal/delivery/http/middleware"
	"dc-post/manager-product-and-warehouse/internal/dto"
	"dc-post/manager-product-and-warehouse/internal/usecase"
	"github.com/gin-gonic/gin"
)

type ShipmentService interface {
	CreateShipment(context.Context, usecase.Actor, dto.CreateShipmentReq) (dto.ShipmentResponse, error)
	ScanTrackingEvent(context.Context, usecase.Actor, int, dto.ScanTrackingEventReq) (dto.TrackingEventResponse, error)
	GetShipmentByTrackingCode(context.Context, usecase.Actor, string) (dto.ShipmentTrackingResponse, error)
}

type ShipmentHandler struct {
	service ShipmentService
}

func NewShipmentHandler(service ShipmentService) *ShipmentHandler {
	return &ShipmentHandler{service: service}
}

func (h *ShipmentHandler) CreateShipment(c *gin.Context) {
	var req dto.CreateShipmentReq
	if !bindJSON(c, &req) {
		return
	}
	if req.OrderID <= 0 || (req.ShipperID != nil && *req.ShipperID <= 0) {
		invalidRequest(c)
		return
	}
	result, err := h.service.CreateShipment(c.Request.Context(), middleware.ActorFromContext(c), req)
	respond(c, result, err, http.StatusCreated, "Tạo vận đơn và xuất kho thành công")
}

func (h *ShipmentHandler) ScanTrackingEvent(c *gin.Context) {
	id, ok := pathID(c, "shipment_id")
	if !ok {
		return
	}
	var req dto.ScanTrackingEventReq
	if !bindJSON(c, &req) {
		return
	}
	if strings.TrimSpace(req.StatusCode) == "" {
		invalidRequest(c)
		return
	}
	result, err := h.service.ScanTrackingEvent(c.Request.Context(), middleware.ActorFromContext(c), id, req)
	respond(c, result, err, http.StatusCreated, "Ghi nhận hành trình thành công")
}

func (h *ShipmentHandler) GetShipmentByTrackingCode(c *gin.Context) {
	result, err := h.service.GetShipmentByTrackingCode(c.Request.Context(), middleware.ActorFromContext(c), c.Param("tracking_code"))
	respond(c, result, err, http.StatusOK, "OK")
}
