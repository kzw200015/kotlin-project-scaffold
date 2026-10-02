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

func TestWrap(t *testing.T) {
	tests := []struct {
		name       string
		fn         HandlerFunc
		wantStatus int
		wantBody   string
	}{
		{"ok", func(*http.Request) (any, error) { return map[string]int{"a": 1}, nil }, 200, `{"a":1}`},
		{"created", func(*http.Request) (any, error) { return Created(map[string]int{"id": 1}), nil }, 201, `{"id":1}`},
		{"no content", func(*http.Request) (any, error) { return nil, nil }, 204, ``},
		{"http error", func(*http.Request) (any, error) { return nil, BadRequest("bad") }, 400, `{"error":"bad"}`},
		{"not found", func(*http.Request) (any, error) { return nil, fmt.Errorf("get: %w", apperr.ErrNotFound) }, 404, `{"error":"get: resource not found"}`},
		{"conflict", func(*http.Request) (any, error) { return nil, apperr.ErrConflict }, 409, `{"error":"resource already exists"}`},
		{"internal", func(*http.Request) (any, error) { return nil, errors.New("db down") }, 500, `{"error":"internal server error"}`},
	}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			rec := httptest.NewRecorder()
			Wrap(log)(tt.fn)(rec, httptest.NewRequestWithContext(t.Context(), http.MethodGet, "/", http.NoBody))
			if rec.Code != tt.wantStatus {
				t.Fatalf("status: want %d, got %d", tt.wantStatus, rec.Code)
			}
			if got := strings.TrimSpace(rec.Body.String()); got != tt.wantBody {
				t.Fatalf("body: want %s, got %s", tt.wantBody, got)
			}
		})
	}
}
