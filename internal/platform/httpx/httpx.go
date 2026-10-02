// Package httpx 提供 HTTP 层通用能力：(any, error) 风格 handler 适配、错误映射、请求解析与中间件。
package httpx

import (
	"log/slog"
	"net/http"
)

// HandlerFunc 是业务 handler 的统一签名，由 Wrap 负责写响应：
//   - (v, nil)                   → 200，body 为 v 的 JSON
//   - (WithStatus(code, v), nil) → 指定状态码，如 Created(v) → 201
//   - (nil, nil)                 → 204 No Content
//   - (_, err)                   → 按 toHTTPError 映射状态码与错误信息
//
// 需要直接操作 ResponseWriter 的场景（文件下载、SSE 等）仍可使用原生 http.HandlerFunc。
type HandlerFunc func(r *http.Request) (any, error)

// Wrap 把 HandlerFunc 适配为 http.HandlerFunc。
func Wrap(log *slog.Logger) func(HandlerFunc) http.HandlerFunc {
	return func(fn HandlerFunc) http.HandlerFunc {
		return func(w http.ResponseWriter, r *http.Request) {
			data, err := fn(r)
			if err != nil {
				he := toHTTPError(err)
				if he.Status >= http.StatusInternalServerError {
					log.ErrorContext(r.Context(), "request failed", "err", err, "method", r.Method, "path", r.URL.Path)
				}
				writeJSON(w, he.Status, errorBody{Error: he.Message})
				return
			}

			switch v := data.(type) {
			case nil:
				w.WriteHeader(http.StatusNoContent)
			case Response:
				writeJSON(w, v.Status, v.Body)
			default:
				writeJSON(w, http.StatusOK, v)
			}
		}
	}
}
