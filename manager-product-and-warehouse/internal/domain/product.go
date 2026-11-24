package domain

// Monetary fields store amounts in hundredths of the currency unit, matching
// the scale of the PostgreSQL DECIMAL(15, 2) columns.
type Product struct {
	ID        int
	Code      string
	Name      string
	UnitPrice int64
}
