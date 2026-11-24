package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"dc-post/manager-product-and-warehouse/config"
	delivery "dc-post/manager-product-and-warehouse/internal/delivery/http"
	"dc-post/manager-product-and-warehouse/internal/delivery/http/handler"
	"dc-post/manager-product-and-warehouse/internal/repository/postgres"
	"dc-post/manager-product-and-warehouse/internal/usecase"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	if err := run(ctx); err != nil {
		stop()
		slog.Error("API server stopped", "error", err)
		os.Exit(1)
	}
}

func run(ctx context.Context) error {
	cfg, err := config.Load()
	if err != nil {
		return fmt.Errorf("load config: %w", err)
	}

	connectCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	pool, err := postgres.NewPool(connectCtx, cfg.DBDSN)
	if err != nil {
		return fmt.Errorf("create PostgreSQL pool: %w", err)
	}
	defer pool.Close()
	if err := pool.Ping(connectCtx); err != nil {
		return fmt.Errorf("ping PostgreSQL: %w", err)
	}
	cancel()

	tx := postgres.NewTxManager(pool)
	products := postgres.NewProductRepository(pool)
	warehouses := postgres.NewWarehouseRepository(pool)
	orders := postgres.NewOrderRepository(pool)
	payments := postgres.NewPaymentRepository(pool)
	shipments := postgres.NewShipmentRepository(pool)

	router := delivery.NewRouter(cfg.JWTSecret,
		handler.NewProductHandler(usecase.NewProductUsecase(products)),
		handler.NewWarehouseHandler(usecase.NewWarehouseUsecase(warehouses, products, tx)),
		handler.NewOrderHandler(usecase.NewOrderUsecase(orders, payments, warehouses, products, shipments, tx)),
		handler.NewShipmentHandler(usecase.NewShipmentUsecase(shipments, orders, payments, warehouses, tx)),
	)
	server := &http.Server{
		Addr:              cfg.HTTPPort,
		Handler:           router,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	serverErr := make(chan error, 1)
	go func() {
		slog.Info("Starting API server", "address", server.Addr)
		serverErr <- server.ListenAndServe()
	}()

	select {
	case err := <-serverErr:
		return fmt.Errorf("serve HTTP: %w", err)
	case <-ctx.Done():
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := server.Shutdown(shutdownCtx); err != nil {
			return fmt.Errorf("shut down HTTP server: %w", errors.Join(err, server.Close()))
		}
		if err := <-serverErr; !errors.Is(err, http.ErrServerClosed) {
			return fmt.Errorf("serve HTTP: %w", err)
		}
		return nil
	}
}
