package httpx

import (
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/kzw200015/go-project-template/internal/platform/apperr"
)

var discardLog = slog.New(slog.NewTextHandler(io.Discard, nil))

func TestWrap(t *testing.T) {
	errCustom := apperr.New(40901, "email already registered")
	tests := []struct {
		name       string
		fn         HandlerFunc
		wantStatus int
		wantBody   string
	}{
		{
			"ok", func(*http.Request) (any, error) { return map[string]int{"a": 1}, nil }, 200,
			`{"code":0,"message":"ok","data":{"a":1}}`,
		},
		{
			"created", func(*http.Request) (any, error) { return Created(map[string]int{"id": 1}), nil }, 201,
			`{"code":0,"message":"ok","data":{"id":1}}`,
		},
		{
			"nil data", func(*http.Request) (any, error) { return nil, nil }, 200,
			`{"code":0,"message":"ok","data":null}`,
		},
		{"wrapped 4xx keeps detail", func(*http.Request) (any, error) {
			return nil, fmt.Errorf("%w: name is required", apperr.ErrInvalidArg)
		}, 400, `{"code":40000,"message":"invalid argument: name is required","data":null}`},
		{
			"domain code", func(*http.Request) (any, error) { return nil, errCustom }, 409,
			`{"code":40901,"message":"email already registered","data":null}`,
		},
		{"5xx hides detail", func(*http.Request) (any, error) {
			return nil, fmt.Errorf("%w: database: dial tcp: refused", apperr.ErrUnavailable)
		}, 503, `{"code":50300,"message":"service unavailable","data":null}`},
		{
			"unknown error", func(*http.Request) (any, error) { return nil, errors.New("db down") }, 500,
			`{"code":50000,"message":"internal server error","data":null}`,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			rec := httptest.NewRecorder()
			Wrap(discardLog)(tt.fn)(rec, httptest.NewRequestWithContext(t.Context(), http.MethodGet, "/", http.NoBody))
			assertResponse(t, rec, tt.wantStatus, tt.wantBody)
		})
	}
}

func TestRecoverer(t *testing.T) {
	h := Recoverer(discardLog)(http.HandlerFunc(func(http.ResponseWriter, *http.Request) { panic("boom") }))
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequestWithContext(t.Context(), http.MethodGet, "/", http.NoBody))
	assertResponse(t, rec, 500, `{"code":50000,"message":"internal server error","data":null}`)
}

func assertResponse(t *testing.T, rec *httptest.ResponseRecorder, wantStatus int, wantBody string) {
	t.Helper()
	if rec.Code != wantStatus {
		t.Fatalf("status: want %d, got %d", wantStatus, rec.Code)
	}
	if got := strings.TrimSpace(rec.Body.String()); got != wantBody {
		t.Fatalf("body:\n want %s\n  got %s", wantBody, got)
	}
}
