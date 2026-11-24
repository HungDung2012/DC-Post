package codegen

import (
	"regexp"
	"testing"
	"time"
)

func TestNewOrderCode_Format(t *testing.T) {
	assertCodes(t, NewOrderCode, `^ORD-20261124-[A-Z0-9]{6}$`)
}

func TestNewTrackingCode_Format(t *testing.T) {
	assertCodes(t, NewTrackingCode, `^TRK-20261124-[A-Z0-9]{6}$`)
}

func assertCodes(t *testing.T, generate func(time.Time) string, pattern string) {
	t.Helper()
	now := time.Date(2026, time.November, 24, 0, 30, 0, 0, time.FixedZone("UTC+7", 7*60*60))
	format := regexp.MustCompile(pattern)
	seen := make(map[string]bool)
	for range 100 {
		code := generate(now)
		if !format.MatchString(code) {
			t.Fatalf("code %q does not match %s", code, pattern)
		}
		if seen[code] {
			t.Fatalf("duplicate code %q", code)
		}
		seen[code] = true
	}
}
