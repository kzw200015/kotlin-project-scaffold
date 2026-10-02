package httpx

import (
	"errors"
	"net/http"

	"github.com/kzw200015/go-project-template/internal/platform/apperr"
)

// Error 是携带 HTTP 状态码的错误，handler 可直接返回。
type Error struct {
	Status  int
	Message string
	Err     error
}

func (e *Error) Error() string {
	if e.Err != nil {
		return e.Message + ": " + e.Err.Error()
	}
	return e.Message
}

func (e *Error) Unwrap() error { return e.Err }

func NewError(status int, msg string) *Error {
	return &Error{Status: status, Message: msg}
}

func BadRequest(msg string) *Error {
	return NewError(http.StatusBadRequest, msg)
}

// toHTTPError 统一把错误映射为 HTTP 响应，未识别的错误一律 500 且不暴露细节。
func toHTTPError(err error) *Error {
	if he, ok := errors.AsType[*Error](err); ok {
		return he
	}
	switch {
	case errors.Is(err, apperr.ErrInvalidArg):
		return &Error{Status: http.StatusBadRequest, Message: err.Error(), Err: err}
	case errors.Is(err, apperr.ErrNotFound):
		return &Error{Status: http.StatusNotFound, Message: err.Error(), Err: err}
	case errors.Is(err, apperr.ErrConflict):
		return &Error{Status: http.StatusConflict, Message: err.Error(), Err: err}
	default:
		return &Error{Status: http.StatusInternalServerError, Message: "internal server error", Err: err}
	}
}
