package com.example.scaffold.auth

import org.apache.ibatis.annotations.Mapper

/** SQL 见 resources/mapper/RoleMapper.xml。 */
@Mapper
interface RoleMapper {
	/**
	 * 用户的角色，按名称排序，同时表明用户是否存在，鉴权时一条查询完成：
	 * 用户不存在时为空列表；存在但没有角色时为 `[null]`（LEFT JOIN 补出的空行）。
	 */
	fun findByUserId(userId: Long): List<Role?>

	/** 授予角色，已有该角色时不变。返回新增的行数。用户不存在时违反外键约束，抛出 DataIntegrityViolationException。 */
	fun add(userId: Long, role: Role): Int

	/** 移除角色，返回删除的行数。 */
	fun remove(userId: Long, role: Role): Int
}
