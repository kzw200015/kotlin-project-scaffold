// Package database 提供 PostgreSQL 连接池与迁移。
package database

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/google/wire"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/kzw200015/go-project-template/internal/platform/config"
)

var ProviderSet = wire.NewSet(NewPool)

// NewPool 创建 pgx 连接池，cfg.Database.AutoMigrate 为 true 时随后执行迁移；返回的 cleanup 由 wire 在应用退出时调用。
// ctx 仅用于启动阶段（建连、Ping、迁移），取消后（如启动期间收到退出信号）立即返回错误。
func NewPool(ctx context.Context, cfg *config.Config, log *slog.Logger) (*pgxpool.Pool, func(), error) {
	pcfg, err := pgxpool.ParseConfig(cfg.Database.DSN)
	if err != nil {
		return nil, nil, fmt.Errorf("parse database dsn: %w", err)
	}
	if cfg.Database.MaxConns > 0 {
		pcfg.MaxConns = cfg.Database.MaxConns
	}
	pcfg.MinConns = cfg.Database.MinConns
	if cfg.Database.MaxConnLifetime > 0 {
		pcfg.MaxConnLifetime = cfg.Database.MaxConnLifetime
	}

	pool, err := connect(ctx, pcfg)
	if err != nil {
		return nil, nil, err
	}

	if cfg.Database.AutoMigrate {
		if err := migrate(ctx, pool, log); err != nil {
			pool.Close()
			return nil, nil, err
		}
	}

	cleanup := func() {
		log.Info("closing database pool")
		pool.Close()
	}
	return pool, cleanup, nil
}

// connect 建立连接池并 Ping，限时 5 秒；迁移可能较慢，不受此超时约束。
func connect(ctx context.Context, pcfg *pgxpool.Config) (*pgxpool.Pool, error) {
	ctx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()

	pool, err := pgxpool.NewWithConfig(ctx, pcfg)
	if err != nil {
		return nil, fmt.Errorf("create pool: %w", err)
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("ping database: %w", err)
	}
	return pool, nil
}
