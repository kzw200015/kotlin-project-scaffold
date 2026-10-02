// Package apperr 定义跨领域通用的业务错误，由 httpx 统一映射为 HTTP 状态码。
package apperr

import "errors"

var (
	ErrNotFound   = errors.New("resource not found")
	ErrConflict   = errors.New("resource already exists")
	ErrInvalidArg = errors.New("invalid argument")
)
