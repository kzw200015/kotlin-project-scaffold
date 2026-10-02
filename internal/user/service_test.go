package user

import (
	"context"
	"errors"
	"testing"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"

	"github.com/kzw200015/go-project-template/internal/platform/apperr"
	"github.com/kzw200015/go-project-template/internal/user/db"
)

// fakeQuerier 嵌入接口，只实现测试需要的方法。
type fakeQuerier struct {
	db.Querier
	getUser    func(ctx context.Context, id int64) (db.User, error)
	createUser func(ctx context.Context, arg db.CreateUserParams) (db.User, error)
}

func (f *fakeQuerier) GetUser(ctx context.Context, id int64) (db.User, error) {
	return f.getUser(ctx, id)
}

func (f *fakeQuerier) CreateUser(ctx context.Context, arg db.CreateUserParams) (db.User, error) {
	return f.createUser(ctx, arg)
}

func TestService_Get_NotFound(t *testing.T) {
	svc := NewService(&fakeQuerier{
		getUser: func(context.Context, int64) (db.User, error) { return db.User{}, pgx.ErrNoRows },
	})
	if _, err := svc.Get(context.Background(), 1); !errors.Is(err, apperr.ErrNotFound) {
		t.Fatalf("want ErrNotFound, got %v", err)
	}
}

func TestService_Create(t *testing.T) {
	tests := []struct {
		name    string
		in      Input
		dbErr   error
		wantErr error
	}{
		{name: "ok", in: Input{Name: " alice ", Email: "a@example.com"}},
		{name: "empty name", in: Input{Email: "a@example.com"}, wantErr: apperr.ErrInvalidArg},
		{name: "bad email", in: Input{Name: "alice", Email: "x"}, wantErr: apperr.ErrInvalidArg},
		{
			name: "duplicate", in: Input{Name: "alice", Email: "a@example.com"},
			dbErr: &pgconn.PgError{Code: pgUniqueViolation}, wantErr: apperr.ErrConflict,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			svc := NewService(&fakeQuerier{
				createUser: func(_ context.Context, arg db.CreateUserParams) (db.User, error) {
					if tt.dbErr != nil {
						return db.User{}, tt.dbErr
					}
					return db.User{ID: 1, Name: arg.Name, Email: arg.Email}, nil
				},
			})
			u, err := svc.Create(context.Background(), tt.in)
			if !errors.Is(err, tt.wantErr) {
				t.Fatalf("want %v, got %v", tt.wantErr, err)
			}
			if err == nil && u.Name != "alice" {
				t.Fatalf("name not trimmed: %q", u.Name)
			}
		})
	}
}
