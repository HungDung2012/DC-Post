package middleware

import (
	"log/slog"
	"runtime/debug"
	"time"

	"dc-post/manager-product-and-warehouse/pkg/apperror"
	"dc-post/manager-product-and-warehouse/pkg/response"
	"github.com/gin-gonic/gin"
)

func RecoveryLogger() gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		defer func() {
			if recover() != nil {
				slog.Error("HTTP handler panicked", "method", c.Request.Method, "route", c.FullPath(), "stack", string(debug.Stack()))
				c.Abort()
				if !c.Writer.Written() {
					response.JSONError(c, apperror.ErrInternalServer)
				}
			}
			slog.Info("HTTP request", "method", c.Request.Method, "route", c.FullPath(),
				"status", c.Writer.Status(), "duration", time.Since(start))
		}()
		c.Next()
	}
}
