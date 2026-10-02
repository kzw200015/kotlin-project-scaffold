package httpx

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/go-chi/chi/v5"
)

type userID int64

func TestParse(t *testing.T) {
	if v, err := parse[int64]("42"); err != nil || v != 42 {
		t.Fatalf("int64: got %v, %v", v, err)
	}
	if v, err := parse[userID]("7"); err != nil || v != 7 {
		t.Fatalf("named int64: got %v, %v", v, err)
	}
	if _, err := parse[int8]("128"); err == nil {
		t.Fatal("int8 overflow: want error")
	}
	if _, err := parse[uint]("-1"); err == nil {
		t.Fatal("uint negative: want error")
	}
	if v, err := parse[bool]("true"); err != nil || !v {
		t.Fatalf("bool: got %v, %v", v, err)
	}
	if v, err := parse[float64]("1.5"); err != nil || v != 1.5 {
		t.Fatalf("float64: got %v, %v", v, err)
	}
	if v, err := parse[string]("abc"); err != nil || v != "abc" {
		t.Fatalf("string: got %v, %v", v, err)
	}
}

func TestPath(t *testing.T) {
	newReq := func(id string) *http.Request {
		rctx := chi.NewRouteContext()
		rctx.URLParams.Add("id", id)
		r := httptest.NewRequestWithContext(t.Context(), http.MethodGet, "/", http.NoBody)
		return r.WithContext(context.WithValue(r.Context(), chi.RouteCtxKey, rctx))
	}

	if v, err := Path[int64](newReq("10"), "id"); err != nil || v != 10 {
		t.Fatalf("got %v, %v", v, err)
	}
	_, err := Path[int64](newReq("abc"), "id")
	if he, ok := errors.AsType[*Error](err); !ok || he.Status != http.StatusBadRequest || he.Message != "invalid id" {
		t.Fatalf("want 400 invalid id, got %v", err)
	}
}

func TestQuery(t *testing.T) {
	r := httptest.NewRequestWithContext(t.Context(), http.MethodGet, "/?limit=5&offset=x", http.NoBody)
	if v := Query[int32](r, "limit", 20); v != 5 {
		t.Fatalf("limit: got %d", v)
	}
	if v := Query[int32](r, "offset", 0); v != 0 {
		t.Fatalf("invalid offset should fall back to default, got %d", v)
	}
	if v := Query(r, "missing", "def"); v != "def" {
		t.Fatalf("missing: got %q", v)
	}
}
