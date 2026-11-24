package domain

import "testing"

func TestInventoryAvailableQuantity(t *testing.T) {
	tests := []struct {
		name     string
		quantity int
		reserved int
		want     int
	}{
		{"no reservations", 10, 0, 10},
		{"partial reservations", 10, 3, 7},
		{"fully reserved", 10, 10, 0},
		{"empty inventory", 0, 0, 0},
		{"over-reserved inventory exposes the deficit", 3, 5, -2},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			inventory := Inventory{Quantity: tt.quantity, ReservedQuantity: tt.reserved}
			if got := inventory.AvailableQuantity(); got != tt.want {
				t.Errorf("AvailableQuantity() = %d, want %d", got, tt.want)
			}
		})
	}
}
