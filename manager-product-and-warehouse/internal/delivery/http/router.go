package http

import (
	"net/http"

	"dc-post/manager-product-and-warehouse/internal/delivery/http/handler"
	"dc-post/manager-product-and-warehouse/internal/delivery/http/middleware"
	"dc-post/manager-product-and-warehouse/internal/usecase"
	"dc-post/manager-product-and-warehouse/pkg/apperror"
	"dc-post/manager-product-and-warehouse/pkg/response"
	"github.com/gin-gonic/gin"
)

func NewRouter(secret string, products *handler.ProductHandler, warehouses *handler.WarehouseHandler, orders *handler.OrderHandler, shipments *handler.ShipmentHandler) *gin.Engine {
	router := gin.New()
	router.Use(middleware.RecoveryLogger(), middleware.CORS())
	router.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "ok"})
	})
	router.NoRoute(func(c *gin.Context) {
		response.JSONError(c, apperror.ErrResourceNotFound)
	})

	api := router.Group("/api/v1", middleware.JWTAuth(secret))
	allRoles := middleware.RequireRoles(usecase.RoleCustomer, usecase.RoleStaff, usecase.RoleAdmin)
	staffOrAdmin := middleware.RequireRoles(usecase.RoleStaff, usecase.RoleAdmin)
	adminOnly := middleware.RequireRoles(usecase.RoleAdmin)

	api.POST("/products", staffOrAdmin, products.Create)
	api.GET("/products", allRoles, products.List)
	api.GET("/products/:id", allRoles, products.GetByID)
	api.PUT("/products/:id", staffOrAdmin, products.Update)

	api.POST("/warehouses", adminOnly, warehouses.CreateWarehouse)
	api.GET("/warehouses", staffOrAdmin, warehouses.ListWarehouses)
	api.PUT("/warehouses/:id", adminOnly, warehouses.UpdateWarehouse)
	api.POST("/warehouses/:id/staff", adminOnly, warehouses.AssignStaff)
	api.DELETE("/warehouses/:id/staff/:user_id", adminOnly, warehouses.RemoveStaff)
	api.GET("/warehouses/:warehouse_id/inventory", staffOrAdmin, warehouses.GetWarehouseInventory)
	api.POST("/inventory/import", staffOrAdmin, warehouses.ImportStock)
	api.POST("/inventory/export", staffOrAdmin, warehouses.ExportManualStock)
	api.POST("/inventory/adjust", staffOrAdmin, warehouses.AdjustStock)
	api.GET("/inventory/transactions", staffOrAdmin, warehouses.ListInventoryTransactions)

	api.POST("/orders", allRoles, orders.CreateOrder)
	api.GET("/orders", allRoles, orders.ListOrders)
	api.GET("/orders/:order_id", allRoles, orders.GetOrderDetail)
	api.PATCH("/orders/:order_id/status", staffOrAdmin, orders.UpdateOrderStatus)
	api.GET("/orders/:order_id/status-history", allRoles, orders.GetOrderStatusHistory)
	api.POST("/orders/:order_id/cancel", allRoles, orders.CancelOrder)
	api.POST("/orders/:order_id/payment/confirm", staffOrAdmin, orders.ConfirmPayment)
	api.PATCH("/refunds/:refund_id/status", staffOrAdmin, orders.UpdateRefundStatus)

	api.POST("/shipments", staffOrAdmin, shipments.CreateShipment)
	api.POST("/shipments/:shipment_id/tracking-events", staffOrAdmin, shipments.ScanTrackingEvent)
	api.GET("/shipments/tracking/:tracking_code", allRoles, shipments.GetShipmentByTrackingCode)

	return router
}
