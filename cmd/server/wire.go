//go:build wireinject

package main

import (
	"context"

	"github.com/google/wire"

	"github.com/kzw200015/go-project-template/internal/health"
	"github.com/kzw200015/go-project-template/internal/platform/config"
	"github.com/kzw200015/go-project-template/internal/platform/database"
	"github.com/kzw200015/go-project-template/internal/platform/logger"
	"github.com/kzw200015/go-project-template/internal/server"
	"github.com/kzw200015/go-project-template/internal/user"
)

func initApp(ctx context.Context) (*app, func(), error) {
	panic(wire.Build(
		// 基础设施
		config.ProviderSet,
		logger.New,
		database.ProviderSet,
		// 领域
		health.ProviderSet,
		user.ProviderSet,
		// 组装
		server.ProviderSet,
		newApp,
	))
}
