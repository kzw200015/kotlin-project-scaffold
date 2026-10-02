// Package server 负责组装各领域路由、全局中间件与 http.Server。
package server

import (
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/google/wire"

	"github.com/kzw200015/go-project-template/internal/health"
	"github.com/kzw200015/go-project-template/internal/platform/config"
	"github.com/kzw200015/go-project-template/internal/platform/httpx"
	"github.com/kzw200015/go-project-template/internal/user"
)

var ProviderSet = wire.NewSet(NewRouter, NewHTTPServer)

// NewRouter 挂载全局中间件与各领域路由，新增领域时在此注入其 Handler 并挂载。
func NewRouter(cfg *config.Config, log *slog.Logger, healthH *health.Handler, userH *user.Handler) http.Handler {
	r := chi.NewRouter()
	r.Use(middleware.RequestID)
	r.Use(clientIP(cfg.Server.ClientIPHeader))
	r.Use(httpx.RequestLogger(log))
	r.Use(httpx.Recoverer(log))
	r.Use(middleware.Timeout(30 * time.Second))

	r.NotFound(httpx.NotFound(log))
	r.MethodNotAllowed(httpx.MethodNotAllowed(log))

	healthH.Register(r)

	r.Route("/api/v1", func(r chi.Router) {
		r.Mount("/users", userH.Routes())
	})
	return r
}

// clientIP 选择获取客户端 IP 的方式，结果通过 middleware.GetClientIP 读取。
// 多级代理且需解析 X-Forwarded-For 时，改用 middleware.ClientIPFromXFF 并配置可信网段。
func clientIP(header string) func(http.Handler) http.Handler {
	if header != "" {
		return middleware.ClientIPFromHeader(header)
	}
	return middleware.ClientIPFromRemoteAddr
}

func NewHTTPServer(cfg *config.Config, h http.Handler) *http.Server {
	return &http.Server{
		Addr:         cfg.Server.Addr,
		Handler:      h,
		ReadTimeout:  cfg.Server.ReadTimeout,
		WriteTimeout: cfg.Server.WriteTimeout,
		IdleTimeout:  cfg.Server.IdleTimeout,
	}
}
