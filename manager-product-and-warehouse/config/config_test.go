package config

import (
	"os"
	"strings"
	"testing"
)

func TestLoadDefaultsHTTPPort(t *testing.T) {
	prepareEnv(t)
	t.Setenv("DB_DSN", "postgres://localhost/dc_post")
	t.Setenv("JWT_SECRET", "test-secret")

	got, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	if got.HTTPPort != ":8082" || got.DBDSN != "postgres://localhost/dc_post" || got.JWTSecret != "test-secret" {
		t.Fatal("configuration differs from the test environment and default port")
	}
}

func TestLoadReadsDotEnvWithProcessEnvironmentPrecedence(t *testing.T) {
	prepareEnv(t)
	if err := os.WriteFile(".env", []byte("DB_DSN=postgres://localhost/dc_post\nJWT_SECRET=file-secret\nHTTP_PORT=8090\n"), 0600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("JWT_SECRET", "process-secret")

	got, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	if got.HTTPPort != ":8090" || got.DBDSN != "postgres://localhost/dc_post" || got.JWTSecret != "process-secret" {
		t.Fatal("configuration did not load the file and process override")
	}
	if _, exists := os.LookupEnv("DB_DSN"); exists {
		t.Error("loading .env modified the process environment")
	}
}

func TestLoadRejectsMissingRequiredSettings(t *testing.T) {
	prepareEnv(t)
	if _, err := Load(); err == nil || !strings.Contains(err.Error(), "DB_DSN") {
		t.Fatalf("expected DB_DSN error, got %v", err)
	}
	t.Setenv("DB_DSN", "postgres://localhost/dc_post")
	if _, err := Load(); err == nil || !strings.Contains(err.Error(), "JWT_SECRET") {
		t.Fatalf("expected JWT_SECRET error, got %v", err)
	}
}

func TestLoadRejectsInvalidPort(t *testing.T) {
	prepareEnv(t)
	t.Setenv("DB_DSN", "postgres://localhost/dc_post")
	t.Setenv("JWT_SECRET", "test-secret")
	t.Setenv("HTTP_PORT", "65536")

	if _, err := Load(); err == nil || !strings.Contains(err.Error(), "HTTP_PORT") {
		t.Fatalf("expected HTTP_PORT error, got %v", err)
	}
}

func TestLoadReportsMalformedDotEnv(t *testing.T) {
	prepareEnv(t)
	if err := os.WriteFile(".env", []byte("DB_DSN='unterminated\n"), 0600); err != nil {
		t.Fatal(err)
	}
	if _, err := Load(); err == nil || !strings.Contains(err.Error(), ".env") {
		t.Fatalf("expected .env error, got %v", err)
	}
}

func prepareEnv(t *testing.T) {
	t.Helper()
	t.Chdir(t.TempDir())
	for _, key := range []string{"DB_DSN", "JWT_SECRET", "HTTP_PORT"} {
		t.Setenv(key, "")
		if err := os.Unsetenv(key); err != nil {
			t.Fatal(err)
		}
	}
}
