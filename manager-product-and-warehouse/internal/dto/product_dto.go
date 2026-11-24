package dto

type CreateProductReq struct {
	ProductCode string  `json:"product_code" binding:"required,max=255"`
	ProductName string  `json:"product_name" binding:"required,max=255"`
	UnitPrice   float64 `json:"unit_price" binding:"required,gt=0"`
}

type UpdateProductReq struct {
	ProductName string  `json:"product_name" binding:"required,max=255"`
	UnitPrice   float64 `json:"unit_price" binding:"required,gt=0"`
}

type ProductListQuery struct {
	Search string `form:"search"`
	Page   int    `form:"page"`
	Limit  int    `form:"limit"`
}

type ProductSummary struct {
	ProductID   int    `json:"product_id"`
	ProductCode string `json:"product_code"`
	ProductName string `json:"product_name"`
}

type ProductResponse struct {
	ProductSummary
	UnitPrice float64 `json:"unit_price"`
}
