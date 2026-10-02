package httpx

import (
	"errors"
	"log/slog"
	"net/http"

	"github.com/kzw200015/go-project-template/internal/platform/apperr"
)

// writeError 把错误写成统一响应：
//   - 错误链中含 *apperr.Error：使用其业务码；4xx 返回完整错误信息（含 %w 包装的细节），
//     5xx 只返回该错误自身的 Message，避免泄露内部细节；
//   - 其他错误：一律视为 apperr.ErrInternal。
//
// 5xx 错误会记录日志。
func writeError(w http.ResponseWriter, r *http.Request, log *slog.Logger, err error) {
	ae, ok := errors.AsType[*apperr.Error](err)
	if !ok {
		ae = apperr.ErrInternal
	}

	status := ae.HTTPStatus()
	msg := err.Error()
	if status >= http.StatusInternalServerError {
		msg = ae.Message
		log.ErrorContext(r.Context(), "request failed", "err", err, "method", r.Method, "path", r.URL.Path)
	}
	writeJSON(w, status, Body{Code: ae.Code, Message: msg})
}
