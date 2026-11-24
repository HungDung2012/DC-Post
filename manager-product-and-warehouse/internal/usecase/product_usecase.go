package usecase

import (
	"context"
	"math"
	"strings"
	"unicode/utf8"

	"dc-post/manager-product-and-warehouse/internal/domain"
	"dc-post/manager-product-and-warehouse/internal/dto"
	"dc-post/manager-product-and-warehouse/pkg/apperror"
)

const (
	RoleStaff = "STAFF"
	RoleAdmin = "ADMIN"
)

type Actor struct {
	UserID int
	Role   string
}

type ProductUsecase struct {
	products domain.ProductRepository
}

func NewProductUsecase(products domain.ProductRepository) *ProductUsecase {
	return &ProductUsecase{products: products}
}

func (uc *ProductUsecase) Create(ctx context.Context, actor Actor, req dto.CreateProductReq) (dto.ProductResponse, error) {
	if err := requireWarehouseRole(actor); err != nil {
		return dto.ProductResponse{}, err
	}
	code, name := strings.TrimSpace(req.ProductCode), strings.TrimSpace(req.ProductName)
	price, err := priceInHundredths(req.UnitPrice)
	if err != nil || !validCatalogText(code) || !validCatalogText(name) {
		return dto.ProductResponse{}, apperror.ErrInvalidRequest
	}
	product := domain.Product{Code: code, Name: name, UnitPrice: price}
	if err := uc.products.Create(ctx, &product); err != nil {
		return dto.ProductResponse{}, err
	}
	return productResponse(product), nil
}

func (uc *ProductUsecase) List(ctx context.Context, query dto.ProductListQuery) ([]dto.ProductResponse, int, error) {
	page, err := listPage(query.Page, query.Limit)
	if err != nil {
		return nil, 0, err
	}
	products, total, err := uc.products.List(ctx, domain.ProductFilter{Page: page, Search: strings.TrimSpace(query.Search)})
	if err != nil {
		return nil, 0, err
	}
	result := make([]dto.ProductResponse, 0, len(products))
	for _, product := range products {
		result = append(result, productResponse(product))
	}
	return result, total, nil
}

func (uc *ProductUsecase) GetByID(ctx context.Context, id int) (dto.ProductResponse, error) {
	if !validID(id) {
		return dto.ProductResponse{}, apperror.ErrInvalidRequest
	}
	product, err := uc.products.GetByID(ctx, id)
	if err != nil {
		return dto.ProductResponse{}, err
	}
	return productResponse(*product), nil
}

func (uc *ProductUsecase) Update(ctx context.Context, actor Actor, id int, req dto.UpdateProductReq) (dto.ProductResponse, error) {
	if err := requireWarehouseRole(actor); err != nil {
		return dto.ProductResponse{}, err
	}
	name := strings.TrimSpace(req.ProductName)
	price, err := priceInHundredths(req.UnitPrice)
	if err != nil || !validID(id) || !validCatalogText(name) {
		return dto.ProductResponse{}, apperror.ErrInvalidRequest
	}
	product, err := uc.products.GetByID(ctx, id)
	if err != nil {
		return dto.ProductResponse{}, err
	}
	product.Name, product.UnitPrice = name, price
	if err := uc.products.Update(ctx, product); err != nil {
		return dto.ProductResponse{}, err
	}
	return productResponse(*product), nil
}

func priceInHundredths(price float64) (int64, error) {
	rounded := math.Round(price * 100)
	// DECIMAL(15, 2) stores up to 15 digits in hundredths.
	if math.IsNaN(rounded) || rounded <= 0 || rounded > 999_999_999_999_999 || rounded/100 != price {
		return 0, apperror.ErrInvalidRequest
	}
	return int64(rounded), nil
}

func productSummary(product domain.Product) dto.ProductSummary {
	return dto.ProductSummary{ProductID: product.ID, ProductCode: product.Code, ProductName: product.Name}
}

func productResponse(product domain.Product) dto.ProductResponse {
	return dto.ProductResponse{ProductSummary: productSummary(product), UnitPrice: float64(product.UnitPrice) / 100}
}

func validCatalogText(value string) bool {
	return value != "" && utf8.ValidString(value) && utf8.RuneCountInString(value) <= 255 && !strings.ContainsRune(value, 0)
}

func validID(id int) bool {
	return id > 0 && id <= math.MaxInt32
}

func requireWarehouseRole(actor Actor) error {
	if !validID(actor.UserID) {
		return apperror.ErrUnauthorized
	}
	if actor.Role != RoleAdmin && actor.Role != RoleStaff {
		return apperror.ErrForbidden
	}
	return nil
}

func requireAdmin(actor Actor) error {
	if err := requireWarehouseRole(actor); err != nil {
		return err
	}
	if actor.Role != RoleAdmin {
		return apperror.ErrForbidden
	}
	return nil
}

func listPage(number, limit int) (domain.Page, error) {
	if number == 0 {
		number = 1
	}
	if limit == 0 {
		limit = 20
	}
	if number < 1 || limit < 1 || number-1 > math.MaxInt/limit {
		return domain.Page{}, apperror.ErrInvalidRequest
	}
	return domain.Page{Limit: limit, Offset: (number - 1) * limit}, nil
}
