# go-project-template

基于 **chi + viper + sqlc + goose + wire** 的 Go Web 服务脚手架，数据库使用 PostgreSQL（pgx/v5）。

## 目录结构

按**领域分包**：一个业务领域的业务逻辑、HTTP 接口放在领域包内，查询 SQL 与 sqlc 生成代码放在领域内的 `db` 子包；跨领域的基础设施放在 `internal/platform`。

```
cmd/server/              程序入口
  main.go                解析参数、加载配置、处理信号
  app.go                 应用生命周期（启动、优雅关闭）
  wire.go                wire 注入器定义（wireinject 构建标签）
  wire_gen.go            wire 生成代码（勿手改）
configs/config.yaml      默认配置
migrations/              goose 迁移文件（全局 schema，同时是 sqlc 的 schema，并 embed 进二进制）
internal/
  user/                  用户领域
    db/                  数据访问（sqlc）
      queries.sql        查询 SQL
      *.go               sqlc 生成代码（勿手改）
    service.go           业务逻辑（依赖 db.Querier 接口）
    handler.go           HTTP 接口与路由
    user.go              包说明与 wire ProviderSet
  health/                存活 / 就绪检查
  server/                挂载各领域路由、全局中间件、http.Server
  platform/              跨领域基础设施
    config/              viper 配置加载（支持 APP_ 前缀环境变量覆盖）
    logger/              slog 日志
    database/            pgx 连接池（auto_migrate 时建连后执行 goose 迁移）
    httpx/               (any, error) handler 适配、错误映射、请求解析、访问日志
    apperr/              通用业务错误（NotFound / Conflict / InvalidArg）
sqlc.yaml
.golangci.yml
Makefile
docker-compose.yml
```

依赖规则：领域包只依赖 `platform/*`，领域之间不直接互相引用；`server` 与 `cmd/server/wire.go` 负责组装。

## 快速开始

```bash
make tools          # 安装 sqlc、golangci-lint
make db-up          # 启动本地 PostgreSQL（docker compose）
make run            # auto_migrate=true 时启动即执行迁移
```

```bash
curl -s localhost:8080/readyz
curl -s -XPOST localhost:8080/api/v1/users -d '{"name":"alice","email":"alice@example.com"}'
curl -s localhost:8080/api/v1/users
```

## 开发流程

### 新增表 / 字段

```bash
make migrate-create name=add_posts   # 在 migrations/ 下生成迁移文件
# 编辑 -- +goose Up / -- +goose Down
make migrate-up                      # 或依赖启动时 auto_migrate
```

### 在已有领域中新增查询

1. 在 `internal/<domain>/db/queries.sql` 写带 `-- name: Xxx :one|:many|:exec|:execrows` 注释的 SQL
2. `make sqlc` 重新生成该领域 `db` 子包内的代码
3. 在 service 中通过 `db.Querier` 调用

### 新增领域（以 order 为例）

1. 建 `internal/order/db/queries.sql`，在 `sqlc.yaml` 追加一个条目（`<<: *go` 复用公共选项，只需改 `queries` 和 `out`，见文件内注释），`make sqlc`
2. 参照 `internal/user` 编写 `service.go`、`handler.go`，以及含 `ProviderSet`（`NewQuerier`、`NewService`、`NewHandler`）的 `order.go`
3. 在 `cmd/server/wire.go` 加入 `order.ProviderSet`，在 `server.NewRouter` 注入 `*order.Handler` 并挂载路由
4. `make wire`

### 代码检查

```bash
make fmt    # gofumpt + gci 格式化
make lint   # golangci-lint（配置见 .golangci.yml）+ sqlc vet
```

生成代码（sqlc、wire）会被自动跳过。

## 配置

配置文件通过 `-config` 指定，任意项都可用环境变量覆盖：键名大写、`.` 换成 `_`、加 `APP_` 前缀。

```bash
APP_DATABASE_DSN="postgres://..." APP_LOG_FORMAT=json ./bin/server -config configs/config.yaml
```

## API

| 方法   | 路径                 | 说明     |
| ------ | -------------------- | -------- |
| GET    | /healthz             | 存活检查 |
| GET    | /readyz              | 就绪检查（含 DB） |
| GET    | /api/v1/users        | 列表（`limit`、`offset`） |
| POST   | /api/v1/users        | 创建     |
| GET    | /api/v1/users/{id}   | 详情     |
| PUT    | /api/v1/users/{id}   | 更新     |
| DELETE | /api/v1/users/{id}   | 删除     |

### 响应格式

所有接口（含 404、405 与 panic）统一返回：

```json
{"code": 0, "message": "ok", "data": {}}
```

- `code`：业务码，`0` 表示成功；失败时为 5 位数，**前三位即 HTTP 状态码**（如 `40901` → 409）
- `message`：提示信息。4xx 返回完整错误信息；5xx 只返回通用信息，细节仅记录日志
- `data`：业务数据，失败时为 `null`

通用错误码定义在 `internal/platform/apperr`：

| code  | 含义 |
| ----- | ---- |
| 40000 | 参数错误 |
| 40400 | 资源不存在 |
| 40500 | 方法不允许 |
| 40900 | 资源冲突 |
| 50000 | 服务内部错误 |
| 50300 | 服务不可用 |

领域自定义错误码在各领域包内定义，例如 `user.ErrEmailTaken = apperr.New(40901, "email already registered")`。
handler 返回错误时可用 `%w` 补充细节：`fmt.Errorf("%w: name is required", apperr.ErrInvalidArg)`。
