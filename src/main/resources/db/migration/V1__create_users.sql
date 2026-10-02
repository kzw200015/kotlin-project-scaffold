CREATE TABLE users
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT        NOT NULL,
    email      TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE users IS '用户';
COMMENT ON COLUMN users.name IS '显示名称';
COMMENT ON COLUMN users.email IS '登录邮箱，全局唯一';
COMMENT ON COLUMN users.created_at IS '注册时间';
