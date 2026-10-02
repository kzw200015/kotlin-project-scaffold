package user

import (
	"context"
	"errors"
	"fmt"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"

	"github.com/kzw200015/go-project-template/internal/platform/apperr"
	"github.com/kzw200015/go-project-template/internal/user/db"
)

const pgUniqueViolation = "23505"

// 用户领域错误。
var ErrEmailTaken = apperr.New(40901, "email already registered")

// Service 只依赖 db.Querier 接口，便于单测替换。
type Service struct {
	q db.Querier
}

func NewService(q db.Querier) *Service {
	return &Service{q: q}
}

type Input struct {
	Name  string `json:"name"`
	Email string `json:"email"`
}

func (in *Input) normalize() error {
	in.Name = strings.TrimSpace(in.Name)
	in.Email = strings.TrimSpace(in.Email)
	if in.Name == "" {
		return fmt.Errorf("%w: name is required", apperr.ErrInvalidArg)
	}
	if !strings.Contains(in.Email, "@") {
		return fmt.Errorf("%w: email is invalid", apperr.ErrInvalidArg)
	}
	return nil
}

func (s *Service) Get(ctx context.Context, id int64) (db.User, error) {
	u, err := s.q.GetUser(ctx, id)
	return u, mapErr(err)
}

func (s *Service) List(ctx context.Context, limit, offset int32) ([]db.User, error) {
	if limit <= 0 || limit > 100 {
		limit = 20
	}
	if offset < 0 {
		offset = 0
	}
	users, err := s.q.ListUsers(ctx, db.ListUsersParams{Limit: limit, Offset: offset})
	return users, mapErr(err)
}

func (s *Service) Create(ctx context.Context, in Input) (db.User, error) {
	if err := in.normalize(); err != nil {
		return db.User{}, err
	}
	u, err := s.q.CreateUser(ctx, db.CreateUserParams(in))
	return u, mapErr(err)
}

func (s *Service) Update(ctx context.Context, id int64, in Input) (db.User, error) {
	if err := in.normalize(); err != nil {
		return db.User{}, err
	}
	u, err := s.q.UpdateUser(ctx, db.UpdateUserParams{ID: id, Name: in.Name, Email: in.Email})
	return u, mapErr(err)
}

func (s *Service) Delete(ctx context.Context, id int64) error {
	n, err := s.q.DeleteUser(ctx, id)
	if err != nil {
		return mapErr(err)
	}
	if n == 0 {
		return apperr.ErrNotFound
	}
	return nil
}

// mapErr 把数据库错误翻译成业务错误，避免上层感知 pgx。
func mapErr(err error) error {
	if err == nil {
		return nil
	}
	if errors.Is(err, pgx.ErrNoRows) {
		return apperr.ErrNotFound
	}
	// users 表上唯一的唯一约束是 email。
	if pgErr, ok := errors.AsType[*pgconn.PgError](err); ok && pgErr.Code == pgUniqueViolation {
		return ErrEmailTaken
	}
	return err
}
