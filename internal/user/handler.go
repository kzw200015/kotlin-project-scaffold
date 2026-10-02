package user

import (
	"log/slog"
	"net/http"

	"github.com/go-chi/chi/v5"

	"github.com/kzw200015/go-project-template/internal/platform/httpx"
)

type Handler struct {
	svc *Service
	log *slog.Logger
}

func NewHandler(svc *Service, log *slog.Logger) *Handler {
	return &Handler{svc: svc, log: log}
}

func (h *Handler) Routes() chi.Router {
	w := httpx.Wrap(h.log)
	r := chi.NewRouter()
	r.Get("/", w(h.list))
	r.Post("/", w(h.create))
	r.Route("/{id}", func(r chi.Router) {
		r.Get("/", w(h.get))
		r.Put("/", w(h.update))
		r.Delete("/", w(h.delete))
	})
	return r
}

func (h *Handler) list(r *http.Request) (any, error) {
	return h.svc.List(r.Context(), httpx.Query[int32](r, "limit", 20), httpx.Query[int32](r, "offset", 0))
}

func (h *Handler) get(r *http.Request) (any, error) {
	id, err := httpx.Path[int64](r, "id")
	if err != nil {
		return nil, err
	}
	return h.svc.Get(r.Context(), id)
}

func (h *Handler) create(r *http.Request) (any, error) {
	in, err := httpx.DecodeJSON[Input](r)
	if err != nil {
		return nil, err
	}
	u, err := h.svc.Create(r.Context(), in)
	if err != nil {
		return nil, err
	}
	return httpx.Created(u), nil
}

func (h *Handler) update(r *http.Request) (any, error) {
	id, err := httpx.Path[int64](r, "id")
	if err != nil {
		return nil, err
	}
	in, err := httpx.DecodeJSON[Input](r)
	if err != nil {
		return nil, err
	}
	return h.svc.Update(r.Context(), id, in)
}

func (h *Handler) delete(r *http.Request) (any, error) {
	id, err := httpx.Path[int64](r, "id")
	if err != nil {
		return nil, err
	}
	return nil, h.svc.Delete(r.Context(), id)
}
