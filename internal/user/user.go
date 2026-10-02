// Package user 是用户领域：业务逻辑（service.go）与 HTTP 接口（handler.go）在本包，
// 查询 SQL 与 sqlc 生成代码在 db 子包。
package user

import (
	"github.com/google/wire"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/kzw200015/go-project-template/internal/user/db"
)

var ProviderSet = wire.NewSet(NewQuerier, NewService, NewHandler)

// NewQuerier 基于连接池创建 sqlc 查询对象；事务场景可使用 db.Queries.WithTx。
func NewQuerier(pool *pgxpool.Pool) db.Querier {
	return db.New(pool)
}
