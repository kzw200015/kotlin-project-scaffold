-- 由 SchemaSnapshotTests 生成，勿手改；表结构变更后执行 ./gradlew updateSchema 更新

CREATE TABLE public.user_roles (
    user_id bigint NOT NULL,
    role text NOT NULL,
    CONSTRAINT user_roles_role_check CHECK ((role = 'ADMIN'::text))
);

COMMENT ON TABLE public.user_roles IS '用户角色，一个用户可有多个角色；用户删除时一并删除';

COMMENT ON COLUMN public.user_roles.user_id IS '用户 id';

COMMENT ON COLUMN public.user_roles.role IS '角色：ADMIN 管理员。取值与代码中的 Role 枚举一致，新增角色时同时修改 CHECK 约束';

CREATE TABLE public.users (
    id bigint NOT NULL,
    name text NOT NULL,
    email text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    password_hash text
);

COMMENT ON TABLE public.users IS '用户';

COMMENT ON COLUMN public.users.name IS '显示名称';

COMMENT ON COLUMN public.users.email IS '登录邮箱，全局唯一';

COMMENT ON COLUMN public.users.created_at IS '注册时间';

COMMENT ON COLUMN public.users.password_hash IS '密码哈希，带算法前缀（如 {bcrypt}$2a$10$...），不存明文；为空表示未设置密码，不能用密码登录';

ALTER TABLE public.users ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;
