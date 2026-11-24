package domain

import "testing"

func TestOrderTransitions(t *testing.T) {
	statuses := []string{
		StatusPending, StatusConfirmed, StatusProcessing, StatusShipping,
		StatusDelivered, StatusCancelled, StatusReturned, "", "UNKNOWN",
	}
	allowed := map[[2]string]bool{
		{"PENDING", "CONFIRMED"}:    true,
		{"PENDING", "CANCELLED"}:    true,
		{"CONFIRMED", "PROCESSING"}: true,
		{"CONFIRMED", "CANCELLED"}:  true,
		{"PROCESSING", "SHIPPING"}:  true,
		{"SHIPPING", "DELIVERED"}:   true,
		{"SHIPPING", "RETURNED"}:    true,
	}
	manual := map[[2]string]bool{
		{"PENDING", "CONFIRMED"}:    true,
		{"CONFIRMED", "PROCESSING"}: true,
	}
	for _, from := range statuses {
		for _, to := range statuses {
			pair := [2]string{from, to}
			if got := CanTransitionOrderStatus(from, to); got != allowed[pair] {
				t.Errorf("order transition %q -> %q = %t, want %t", from, to, got, allowed[pair])
			}
			if got := CanUpdateOrderStatus(from, to); got != manual[pair] {
				t.Errorf("manual status update %q -> %q = %t, want %t", from, to, got, manual[pair])
			}
		}
	}
}
