package com.example.scaffold.auth

import com.example.scaffold.user.UserErrors
import com.example.scaffold.user.UserMapper
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

/** 角色管理。授予或移除后下一个请求即生效：角色在每个请求时从数据库加载（见 TokenService）。 */
@Service
class RoleService(private val mapper: RoleMapper, private val users: UserMapper) {

	/**
	 * 授予角色，已有时不变。用户是否存在靠外键约束判断，而不是先查再插（并发下用户可能在两步之间被删除）。
	 * 与 UserService.create 一样，这种捕获只在没有外层事务时有效。
	 */
	fun grant(userId: Long, role: Role) {
		try {
			mapper.add(userId, role)
		} catch (e: DataIntegrityViolationException) {
			UserErrors.notFound(userId, e)
		}
	}

	/** 移除角色，没有该角色时不变。先查用户只为区分 404，两步之间用户被删除也只是删除 0 行，无需事务。 */
	fun revoke(userId: Long, role: Role) {
		users.findById(userId) ?: UserErrors.notFound(userId)
		mapper.remove(userId, role)
	}
}
