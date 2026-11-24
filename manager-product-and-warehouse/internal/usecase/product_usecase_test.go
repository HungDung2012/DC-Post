package usecase

import (
	"context"
	"errors"
	"testing"

	"dc-post/manager-product-and-warehouse/internal/domain"
	"dc-post/manager-product-and-warehouse/internal/dto"
	"dc-post/manager-product-and-warehouse/pkg/apperror"
)

type productMemory struct {
	products map[int]domain.Product
	filter   domain.ProductFilter
}

func (p *productMemory) Create(_ context.Context, product *domain.Product) error {
	for _, existing := range p.products {
		if existing.Code == product.Code {
			return apperror.ErrDuplicateCode
		}
	}
	product.ID = len(p.products) + 1
	p.products[product.ID] = *product
	return nil
}

func (p *productMemory) GetByID(_ context.Context, id int) (*domain.Product, error) {
	product, ok := p.products[id]
	if !ok {
		return nil, apperror.ErrResourceNotFound
	}
	return &product, nil
}

func (p *productMemory) List(_ context.Context, filter domain.ProductFilter) ([]domain.Product, int, error) {
	p.filter = filter
	return []domain.Product{p.products[1]}, 31, nil
}

func (p *productMemory) Update(_ context.Context, product *domain.Product) error {
	p.products[product.ID] = *product
	return nil
}

func TestProductCatalogPersistsPriceInHundredths(t *testing.T) {
	repo := &productMemory{products: map[int]domain.Product{}}
	uc := NewProductUsecase(repo)
	ctx := t.Context()
	staff := Actor{UserID: 22, Role: "STAFF"}
	req := dto.CreateProductReq{ProductCode: "P-1", ProductName: "Carton", UnitPrice: 12.34}

	created, err := uc.Create(ctx, staff, req)
	if err != nil || created.ProductID != 1 || repo.products[1].UnitPrice != 1234 {
		t.Fatalf("create = %+v, %v; stored = %+v", created, err, repo.products[1])
	}
	if _, err := uc.Create(ctx, staff, req); !errors.Is(err, apperror.ErrDuplicateCode) {
		t.Fatalf("duplicate: %v", err)
	}
	updated, err := uc.Update(ctx, staff, 1, dto.UpdateProductReq{ProductName: "Box", UnitPrice: 21.05})
	if err != nil || updated.ProductCode != "P-1" || repo.products[1].UnitPrice != 2105 {
		t.Fatalf("update = %+v, %v; stored = %+v", updated, err, repo.products[1])
	}
	got, err := uc.GetByID(ctx, 1)
	if err != nil || got.ProductName != "Box" || got.UnitPrice != 21.05 {
		t.Fatalf("get = %+v, %v", got, err)
	}
	if _, err := uc.GetByID(ctx, 2); !errors.Is(err, apperror.ErrResourceNotFound) {
		t.Fatalf("missing product: %v", err)
	}
	rows, total, err := uc.List(ctx, dto.ProductListQuery{Search: "box", Page: 2, Limit: 5})
	if err != nil || len(rows) != 1 || total != 31 || repo.filter.Search != "box" || repo.filter.Offset != 5 || repo.filter.Limit != 5 {
		t.Fatalf("list = %+v, %d, %v; filter = %+v", rows, total, err, repo.filter)
	}
}

func TestProductCreateRejectsUnauthorizedOrInvalidPrice(t *testing.T) {
	repo := &productMemory{products: map[int]domain.Product{}}
	uc := NewProductUsecase(repo)
	req := dto.CreateProductReq{ProductCode: "P-1", ProductName: "Carton", UnitPrice: 12.34}
	if _, err := uc.Create(t.Context(), Actor{UserID: 7, Role: "CUSTOMER"}, req); !errors.Is(err, apperror.ErrForbidden) {
		t.Fatalf("customer create: %v", err)
	}
	req.UnitPrice = 12.345
	if _, err := uc.Create(t.Context(), Actor{UserID: 22, Role: "STAFF"}, req); !errors.Is(err, apperror.ErrInvalidRequest) {
		t.Fatalf("sub-cent price: %v", err)
	}
	if len(repo.products) != 0 {
		t.Fatal("invalid products were persisted")
	}
}
