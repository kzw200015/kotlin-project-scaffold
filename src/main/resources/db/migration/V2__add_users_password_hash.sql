-- 已有用户没有密码，允许为空：为空时不能用密码登录
ALTER TABLE users ADD COLUMN password_hash TEXT;

COMMENT ON COLUMN users.password_hash IS '密码哈希，带算法前缀（如 {bcrypt}$2a$10$...），不存明文；为空表示未设置密码，不能用密码登录';
