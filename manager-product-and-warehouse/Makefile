.PHONY: test db-up migrate api

test:
	go test ./... -v

db-up:
	docker compose up -d --wait postgres

migrate: db-up
	docker compose run --rm --no-deps migrate

api:
	go run ./cmd/api
