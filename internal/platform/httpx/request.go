package httpx

import (
	"encoding/json"
	"net/http"
	"reflect"
	"strconv"

	"github.com/go-chi/chi/v5"
)

const maxBodyBytes = 1 << 20 // 1 MiB

// Scalar 是可从路径参数、查询参数解析的类型，包括以它们为底层类型的自定义类型（如 type UserID int64）。
type Scalar interface {
	~string | ~bool |
		~int | ~int8 | ~int16 | ~int32 | ~int64 |
		~uint | ~uint8 | ~uint16 | ~uint32 | ~uint64 |
		~float32 | ~float64
}

// DecodeJSON 把 JSON 请求体解析为 T，失败返回 400。
func DecodeJSON[T any](r *http.Request) (T, error) {
	var v T
	dec := json.NewDecoder(http.MaxBytesReader(nil, r.Body, maxBodyBytes))
	if err := dec.Decode(&v); err != nil {
		return v, &Error{Status: http.StatusBadRequest, Message: "invalid json body", Err: err}
	}
	return v, nil
}

// Path 把路径参数解析为 T，失败返回 400。
func Path[T Scalar](r *http.Request, key string) (T, error) {
	v, err := parse[T](chi.URLParam(r, key))
	if err != nil {
		return v, &Error{Status: http.StatusBadRequest, Message: "invalid " + key, Err: err}
	}
	return v, nil
}

// Query 把查询参数解析为 T，缺省或非法时返回 def。
func Query[T Scalar](r *http.Request, key string, def T) T {
	s := r.URL.Query().Get(key)
	if s == "" {
		return def
	}
	v, err := parse[T](s)
	if err != nil {
		return def
	}
	return v
}

// parse 按 T 的底层类型选择 strconv 解析函数，位宽取自 T，超出范围会报错而不是截断。
func parse[T Scalar](s string) (T, error) {
	var v T
	rv := reflect.ValueOf(&v).Elem()
	switch rv.Kind() {
	case reflect.String:
		rv.SetString(s)
	case reflect.Bool:
		b, err := strconv.ParseBool(s)
		if err != nil {
			return v, err
		}
		rv.SetBool(b)
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		n, err := strconv.ParseInt(s, 10, rv.Type().Bits())
		if err != nil {
			return v, err
		}
		rv.SetInt(n)
	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
		n, err := strconv.ParseUint(s, 10, rv.Type().Bits())
		if err != nil {
			return v, err
		}
		rv.SetUint(n)
	case reflect.Float32, reflect.Float64:
		f, err := strconv.ParseFloat(s, rv.Type().Bits())
		if err != nil {
			return v, err
		}
		rv.SetFloat(f)
	default:
		panic("httpx: unsupported kind " + rv.Kind().String()) // Scalar 约束保证不可达
	}
	return v, nil
}
