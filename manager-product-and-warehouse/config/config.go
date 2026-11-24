package config

import (
	"errors"
	"fmt"
	"io/fs"
	"os"
	"strconv"
	"strings"

	"github.com/joho/godotenv"
)

type Config struct {
	DBDSN     string
	HTTPPort  string
	JWTSecret string
}

// Load reads settings from the environment and a local .env file, in that order.
func Load() (*Config, error) {
	dotEnv, err := godotenv.Read(".env")
	if err != nil && !errors.Is(err, fs.ErrNotExist) {
		return nil, fmt.Errorf("read .env: %w", err)
	}

	lookup := func(key string) string {
		if value, ok := os.LookupEnv(key); ok {
			return value
		}
		return dotEnv[key]
	}

	cfg := &Config{
		DBDSN:     lookup("DB_DSN"),
		JWTSecret: lookup("JWT_SECRET"),
	}
	if strings.TrimSpace(cfg.DBDSN) == "" {
		return nil, errors.New("DB_DSN is required")
	}
	if strings.TrimSpace(cfg.JWTSecret) == "" {
		return nil, errors.New("JWT_SECRET is required")
	}

	port := lookup("HTTP_PORT")
	if port == "" {
		port = "8082"
	}
	number, err := strconv.ParseUint(strings.TrimPrefix(port, ":"), 10, 16)
	if err != nil || number == 0 {
		return nil, errors.New("HTTP_PORT must be a port between 1 and 65535")
	}
	cfg.HTTPPort = ":" + strconv.FormatUint(number, 10)
	return cfg, nil
}
