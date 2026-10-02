package httpx

import (
	"encoding/json"
	"net/http"
)

// Response 用于返回非 200 的成功响应。
type Response struct {
	Status int
	Body   any
}

func WithStatus(status int, body any) Response {
	return Response{Status: status, Body: body}
}

func Created(body any) Response {
	return WithStatus(http.StatusCreated, body)
}

type errorBody struct {
	Error string `json:"error"`
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	if v == nil {
		w.WriteHeader(status)
		return
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
