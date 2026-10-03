package com.example.scaffold.platform

/**
 * 角色，存在 `user_roles` 表中，登录时写入 token 的 [ROLES_CLAIM] 声明，鉴权时转成 `ROLE_` 前缀的权限（如 `ROLE_ADMIN`）。
 * 普通用户没有角色，登录即可访问 `authenticated` 的接口。新增角色时同时修改 `user_roles.role` 的 CHECK 约束。
 */
enum class Role {
	ADMIN,
}

/** token 中存放角色名列表的声明。 */
const val ROLES_CLAIM = "roles"
