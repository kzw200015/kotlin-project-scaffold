package httpx

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
)

const maxBodyBytes = 1 << 20 // 1 MiB

// DecodeJSON 解析 JSON 请求体，失败返回 400。
func DecodeJSON(r *http.Request, dst any) error {
	dec := json.NewDecoder(http.MaxBytesReader(nil, r.Body, maxBodyBytes))
	if err := dec.Decode(dst); err != nil {
		return &Error{Status: http.StatusBadRequest, Message: "invalid json body", Err: err}
	}
	return nil
}

// PathInt64 解析正整数路径参数，失败返回 400。
func PathInt64(r *http.Request, key string) (int64, error) {
	v, err := strconv.ParseInt(chi.URLParam(r, key), 10, 64)
	if err != nil || v <= 0 {
		return 0, BadRequest("invalid " + key)
	}
	return v, nil
}

// QueryInt32 解析查询参数，缺省或非法时返回 def。
func QueryInt32(r *http.Request, key string, def int32) int32 {
	v, err := strconv.ParseInt(r.URL.Query().Get(key), 10, 32)
	if err != nil {
		return def
	}
	return int32(v)
}
