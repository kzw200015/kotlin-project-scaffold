package httpx

import (
	"encoding/json"
	"net/http"

	"github.com/kzw200015/go-project-template/internal/platform/apperr"
)

// Body 是所有接口统一的响应格式。
type Body struct {
	Code    int    `json:"code"`    // 业务码，0 表示成功
	Message string `json:"message"` // 提示信息
	Data    any    `json:"data"`    // 业务数据，失败时为 null
}

// Response 用于返回非 200 的成功响应。
type Response struct {
	Status int
	Data   any
}

func WithStatus(status int, data any) Response {
	return Response{Status: status, Data: data}
}

func Created(data any) Response {
	return WithStatus(http.StatusCreated, data)
}

func writeOK(w http.ResponseWriter, status int, data any) {
	writeJSON(w, status, Body{Code: apperr.CodeOK, Message: "ok", Data: data})
}

func writeJSON(w http.ResponseWriter, status int, body Body) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}
