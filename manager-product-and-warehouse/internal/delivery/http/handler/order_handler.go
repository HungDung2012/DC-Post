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

type OrderService interface {
	CreateOrder(context.Context, usecase.Actor, dto.CreateOrderReq) (dto.OrderResponse, error)
	ListOrders(context.Context, usecase.Actor, dto.OrderListQuery) ([]dto.OrderSummary, int, error)
	GetOrderDetail(context.Context, usecase.Actor, int) (dto.OrderDetailResponse, error)
	UpdateOrderStatus(context.Context, usecase.Actor, int, dto.UpdateOrderStatusReq) (dto.OrderStatusChangeResponse, error)
	GetOrderStatusHistory(context.Context, usecase.Actor, int) ([]dto.OrderStatusHistoryResponse, error)
	CancelOrder(context.Context, usecase.Actor, int) (dto.CancelOrderResponse, error)
	ConfirmPayment(context.Context, usecase.Actor, int, dto.ConfirmPaymentReq) (dto.PaymentResponse, error)
	UpdateRefundStatus(context.Context, usecase.Actor, int, dto.UpdateRefundStatusReq) (dto.RefundResponse, error)
}

type OrderHandler struct {
	service OrderService
}

func NewOrderHandler(service OrderService) *OrderHandler {
	return &OrderHandler{service: service}
}

func (h *OrderHandler) CreateOrder(c *gin.Context) {
	var req dto.CreateOrderReq
	if !bindJSON(c, &req) {
		return
	}
	actor := middleware.ActorFromContext(c)
	if (actor.Role != usecase.RoleCustomer && req.CustomID <= 0) ||
		req.WarehouseID <= 0 || req.PaymentMethod == "" || len(req.Items) == 0 {
		invalidRequest(c)
		return
	}
	for _, item := range req.Items {
		if item.ProductID <= 0 || item.Quantity <= 0 {
			invalidRequest(c)
			return
		}
	}
	result, err := h.service.CreateOrder(c.Request.Context(), actor, req)
	respond(c, result, err, http.StatusCreated, "Tạo đơn hàng thành công")
}

func (h *OrderHandler) ListOrders(c *gin.Context) {
	var query dto.OrderListQuery
	if !bindQuery(c, &query) {
		return
	}
	result, total, err := h.service.ListOrders(c.Request.Context(), middleware.ActorFromContext(c), query)
	respondPage(c, result, total, query.Page, query.Limit, err)
}

func (h *OrderHandler) GetOrderDetail(c *gin.Context) {
	id, ok := pathID(c, "order_id")
	if !ok {
		return
	}
	result, err := h.service.GetOrderDetail(c.Request.Context(), middleware.ActorFromContext(c), id)
	respond(c, result, err, http.StatusOK, "OK")
}

func (h *OrderHandler) UpdateOrderStatus(c *gin.Context) {
	id, ok := pathID(c, "order_id")
	if !ok {
		return
	}
	var req dto.UpdateOrderStatusReq
	if !bindJSON(c, &req) {
		return
	}
	if strings.TrimSpace(req.NewStatusCode) == "" {
		invalidRequest(c)
		return
	}
	result, err := h.service.UpdateOrderStatus(c.Request.Context(), middleware.ActorFromContext(c), id, req)
	respond(c, result, err, http.StatusOK, "Cập nhật trạng thái đơn hàng thành công")
}

func (h *OrderHandler) GetOrderStatusHistory(c *gin.Context) {
	id, ok := pathID(c, "order_id")
	if !ok {
		return
	}
	result, err := h.service.GetOrderStatusHistory(c.Request.Context(), middleware.ActorFromContext(c), id)
	respond(c, result, err, http.StatusOK, "OK")
}

func (h *OrderHandler) CancelOrder(c *gin.Context) {
	id, ok := pathID(c, "order_id")
	if !ok {
		return
	}
	result, err := h.service.CancelOrder(c.Request.Context(), middleware.ActorFromContext(c), id)
	respond(c, result, err, http.StatusOK, "Hủy đơn hàng thành công")
}

func (h *OrderHandler) ConfirmPayment(c *gin.Context) {
	id, ok := pathID(c, "order_id")
	if !ok {
		return
	}
	var req dto.ConfirmPaymentReq
	if !bindJSON(c, &req) {
		return
	}
	if strings.TrimSpace(req.TransactionCode) == "" {
		invalidRequest(c)
		return
	}
	result, err := h.service.ConfirmPayment(c.Request.Context(), middleware.ActorFromContext(c), id, req)
	respond(c, result, err, http.StatusOK, "Xác nhận thanh toán thành công")
}

func (h *OrderHandler) UpdateRefundStatus(c *gin.Context) {
	id, ok := pathID(c, "refund_id")
	if !ok {
		return
	}
	var req dto.UpdateRefundStatusReq
	if !bindJSON(c, &req) {
		return
	}
	if strings.TrimSpace(req.RefundStatus) == "" {
		invalidRequest(c)
		return
	}
	result, err := h.service.UpdateRefundStatus(c.Request.Context(), middleware.ActorFromContext(c), id, req)
	respond(c, result, err, http.StatusOK, "Cập nhật trạng thái hoàn tiền thành công")
}
