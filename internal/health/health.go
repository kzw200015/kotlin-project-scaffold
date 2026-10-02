// Package health 提供存活与就绪检查接口。
package health

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/wire"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/kzw200015/go-project-template/internal/platform/apperr"
	"github.com/kzw200015/go-project-template/internal/platform/httpx"
)

var ProviderSet = wire.NewSet(NewHandler)

type Handler struct {
	pool *pgxpool.Pool
	log  *slog.Logger
}

func NewHandler(pool *pgxpool.Pool, log *slog.Logger) *Handler {
	return &Handler{pool: pool, log: log}
}

func (h *Handler) Register(r chi.Router) {
	w := httpx.Wrap(h.log)
	r.Get("/healthz", w(h.live))
	r.Get("/readyz", w(h.ready))
}

// live 仅表示进程存活。
func (h *Handler) live(*http.Request) (any, error) {
	return map[string]string{"status": "ok"}, nil
}

// ready 检查依赖（数据库）是否可用。
func (h *Handler) ready(r *http.Request) (any, error) {
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()
	if err := h.pool.Ping(ctx); err != nil {
		return nil, fmt.Errorf("%w: database: %w", apperr.ErrUnavailable, err)
	}
	return map[string]string{"status": "ok"}, nil
}
