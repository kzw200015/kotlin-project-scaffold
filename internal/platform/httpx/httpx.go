// Package httpx 提供 HTTP 层通用能力：(any, error) 风格 handler 适配、统一响应格式、请求解析与中间件。
package httpx

import (
	"log/slog"
	"net/http"

	"github.com/kzw200015/go-project-template/internal/platform/apperr"
)

// HandlerFunc 是业务 handler 的统一签名，由 Wrap 写成统一响应 {"code", "message", "data"}：
//   - (v, nil)                   → 200，data 为 v
//   - (WithStatus(code, v), nil) → 指定 HTTP 状态码，如 Created(v) → 201
//   - (nil, nil)                 → 200，data 为 null
//   - (_, err)                   → 按 err 中的 *apperr.Error 决定业务码与 HTTP 状态码，见 writeError
//
// 需要直接操作 ResponseWriter 的场景（文件下载、SSE 等）仍可使用原生 http.HandlerFunc。
type HandlerFunc func(r *http.Request) (any, error)

// Wrap 把 HandlerFunc 适配为 http.HandlerFunc。
func Wrap(log *slog.Logger) func(HandlerFunc) http.HandlerFunc {
	return func(fn HandlerFunc) http.HandlerFunc {
		return func(w http.ResponseWriter, r *http.Request) {
			data, err := fn(r)
			if err != nil {
				writeError(w, r, log, err)
				return
			}
			if resp, ok := data.(Response); ok {
				writeOK(w, resp.Status, resp.Data)
				return
			}
			writeOK(w, http.StatusOK, data)
		}
	}
}

// NotFound 以统一格式响应未匹配的路由。
func NotFound(log *slog.Logger) http.HandlerFunc {
	return Wrap(log)(func(*http.Request) (any, error) { return nil, apperr.ErrNotFound })
}

// MethodNotAllowed 以统一格式响应不支持的请求方法。
func MethodNotAllowed(log *slog.Logger) http.HandlerFunc {
	return Wrap(log)(func(*http.Request) (any, error) { return nil, apperr.ErrMethodNotAllowed })
}
