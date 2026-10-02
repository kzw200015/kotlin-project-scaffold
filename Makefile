APP          := server
CONFIG       ?= configs/config.yaml
DATABASE_DSN ?= postgres://postgres:postgres@localhost:5432/app?sslmode=disable
MIGRATIONS   := migrations

SQLC_VERSION          := v1.31.1
GOLANGCI_LINT_VERSION := v2.14.0
GOOSE_VERSION := v3.28.0
GOOSE := GOOSE_DRIVER=postgres GOOSE_DBSTRING="$(DATABASE_DSN)" GOOSE_MIGRATION_DIR=$(MIGRATIONS) \
	go run github.com/pressly/goose/v3/cmd/goose@$(GOOSE_VERSION)

.PHONY: help tools generate sqlc wire build run test lint fmt tidy \
	migrate-create migrate-up migrate-down migrate-status db-up db-down

help: ## 显示帮助
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

tools: ## 安装 sqlc、golangci-lint（wire 通过 go tool 使用，goose 通过 go run 使用，无需安装）
	go install github.com/sqlc-dev/sqlc/cmd/sqlc@$(SQLC_VERSION)
	go install github.com/golangci/golangci-lint/v2/cmd/golangci-lint@$(GOLANGCI_LINT_VERSION)

generate: sqlc wire ## 生成全部代码

sqlc: ## 根据 SQL 生成数据访问代码
	sqlc generate

wire: ## 生成依赖注入代码
	go tool wire ./cmd/server

build: ## 编译
	go build -o bin/$(APP) ./cmd/server

run: ## 本地运行
	go run ./cmd/server -config $(CONFIG)

test: ## 运行测试
	go test -race ./...

lint: ## 静态检查（golangci-lint + sqlc vet）
	golangci-lint run ./...
	sqlc vet

fmt: ## 格式化代码（gofumpt + gci）
	golangci-lint fmt ./...

tidy:
	go mod tidy

migrate-create: ## 新建迁移：make migrate-create name=add_xxx
	@test -n "$(name)" || (echo "usage: make migrate-create name=xxx" && exit 1)
	$(GOOSE) -s create $(name) sql

migrate-up: ## 执行全部迁移
	$(GOOSE) up

migrate-down: ## 回滚一个版本
	$(GOOSE) down

migrate-status: ## 查看迁移状态
	$(GOOSE) status

db-up: ## 启动本地 PostgreSQL
	docker compose up -d postgres

db-down: ## 停止本地 PostgreSQL
	docker compose down
