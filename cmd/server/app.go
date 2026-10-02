package main

import (
	"context"
	"log/slog"
	"net/http"

	"github.com/kzw200015/go-project-template/internal/platform/config"
)

type app struct {
	cfg *config.Config
	log *slog.Logger
	srv *http.Server
}

func newApp(cfg *config.Config, log *slog.Logger, srv *http.Server) *app {
	return &app{cfg: cfg, log: log, srv: srv}
}

// Run 启动 HTTP 服务并阻塞，直到服务自己停止，或 ctx 取消后将其优雅关闭。
func (a *app) Run(ctx context.Context) error {
	// 后台运行服务。ListenAndServe 会阻塞到服务停止，返回值发到 serveErr；
	// 缓冲为 1，保证没人接收时 goroutine 也能发送完退出。
	serveErr := make(chan error, 1)
	go func() {
		a.log.Info("http server listening", "addr", a.srv.Addr)
		serveErr <- a.srv.ListenAndServe()
	}()

	select {
	case err := <-serveErr:
		// 分支一：服务自己停了（如端口被占用）。
		return err
	case <-ctx.Done():
		// 分支二：收到退出信号，手动关闭服务。
	}

	a.log.Info("shutting down http server")
	// ctx 已取消，关闭超时要基于一个不会被取消的 ctx。
	shutdownCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), a.cfg.Server.ShutdownTimeout)
	defer cancel()
	// 停止接收新请求，等待处理中的请求完成后返回。
	return a.srv.Shutdown(shutdownCtx)
}
