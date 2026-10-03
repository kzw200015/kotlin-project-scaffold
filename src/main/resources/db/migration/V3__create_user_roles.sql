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
