CREATE TABLE users
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          TEXT        NOT NULL,
    email         TEXT        NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE users IS '用户';
COMMENT ON COLUMN users.name IS '显示名称';
COMMENT ON COLUMN users.email IS '登录邮箱，去掉首尾空白并转小写后存储，全局唯一';
COMMENT ON COLUMN users.password_hash IS '密码哈希，带算法前缀（如 {bcrypt}$2a$10$...），不存明文';
COMMENT ON COLUMN users.created_at IS '注册时间';

-- 只存额外授予的角色，普通用户不存行：登录即为普通用户
CREATE TABLE user_roles
(
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    TEXT   NOT NULL CHECK (role IN ('ADMIN')),
    PRIMARY KEY (user_id, role)
);

COMMENT ON TABLE user_roles IS '用户角色，一个用户可有多个角色；用户删除时一并删除';
COMMENT ON COLUMN user_roles.user_id IS '用户 id';
COMMENT ON COLUMN user_roles.role IS '角色：ADMIN 管理员。取值与代码中的 Role 枚举一致，新增角色时同时修改 CHECK 约束';
