// Package apperr 定义带业务码的错误，由 httpx 统一写成响应。
//
// 业务码为 5 位数，前三位即 HTTP 状态码（如 40401 → 404），0 表示成功。
// 各领域可用 New 定义自己的错误，如 apperr.New(40901, "email already registered")。
// 需要补充细节时用 %w 包装：fmt.Errorf("%w: name is required", apperr.ErrInvalidArg)。
package apperr

import "net/http"

// CodeOK 是成功响应的业务码。
const CodeOK = 0

// 通用错误。
var (
	ErrInvalidArg       = New(40000, "invalid argument")
	ErrNotFound         = New(40400, "resource not found")
	ErrMethodNotAllowed = New(40500, "method not allowed")
	ErrConflict         = New(40900, "resource already exists")
	ErrInternal         = New(50000, "internal server error")
	ErrUnavailable      = New(50300, "service unavailable")
)

// Error 是带业务码的错误。
type Error struct {
	Code    int
	Message string
}

func New(code int, msg string) *Error {
	return &Error{Code: code, Message: msg}
}

func (e *Error) Error() string { return e.Message }

// HTTPStatus 由业务码前三位得出 HTTP 状态码，不合法时视为 500。
func (e *Error) HTTPStatus() int {
	status := e.Code / 100
	if status < http.StatusBadRequest || status > 599 {
		return http.StatusInternalServerError
	}
	return status
}
