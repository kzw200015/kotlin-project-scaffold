package httpx

import (
	"errors"
	"log/slog"
	"net/http"
	"runtime/debug"
	"time"

	"github.com/go-chi/chi/v5/middleware"

	"github.com/kzw200015/go-project-template/internal/platform/apperr"
)

// RequestLogger 用 slog 记录访问日志。
func RequestLogger(log *slog.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)
			start := time.Now()
			defer func() {
				log.InfoContext(r.Context(), "http request",
					"method", r.Method,
					"path", r.URL.Path,
					"status", ww.Status(),
					"bytes", ww.BytesWritten(),
					"duration", time.Since(start),
					"client_ip", middleware.GetClientIP(r.Context()),
					"request_id", middleware.GetReqID(r.Context()),
				)
			}()
			next.ServeHTTP(ww, r)
		})
	}
}

// Recoverer 捕获 panic，记录堆栈并以统一格式返回 500。
func Recoverer(log *slog.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			defer func() {
				rec := recover()
				if rec == nil {
					return
				}
				// http.ErrAbortHandler 是中断响应的约定信号，交还给 net/http 处理。
				if err, ok := rec.(error); ok && errors.Is(err, http.ErrAbortHandler) {
					panic(rec)
				}
				log.ErrorContext(r.Context(), "panic recovered", "panic", rec, "stack", string(debug.Stack()))
				writeJSON(w, http.StatusInternalServerError, Body{Code: apperr.ErrInternal.Code, Message: apperr.ErrInternal.Message})
			}()
			next.ServeHTTP(w, r)
		})
	}
}
