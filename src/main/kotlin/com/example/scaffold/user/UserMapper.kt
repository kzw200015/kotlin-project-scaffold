package com.example.scaffold.user

import com.example.scaffold.platform.Role
import org.apache.ibatis.annotations.Mapper

/**
 * SQL 见 resources/mapper/UserMapper.xml。多个参数无需 @Param，XML 中直接按参数名引用（依赖 javaParameters 编译选项）。
 */
@Mapper
interface UserMapper {
	/** 插入并返回新行（含数据库生成的 id、createdAt）。 */
	fun insert(name: String, email: String, passwordHash: String): UserRecord

	fun findById(id: Long): UserRecord?

	fun findCredentialByEmail(email: String): UserCredential?

	/** 按 name / email 模糊搜索，keyword 为空时不过滤；keyword 中的通配符需调用方先用 escapeLike 转义。 */
	fun search(keyword: String?, limit: Int, offset: Int): List<UserRecord>

	/** 与 [search] 条件相同的总数。 */
	fun count(keyword: String?): Long

	fun updateName(id: Long, name: String): Int

	fun deleteById(id: Long): Int

	/** 用户的角色，按名称排序；没有角色时为空列表。 */
	fun findRoles(userId: Long): List<Role>

	/** 授予角色，已有该角色时不变。返回新增的行数。 */
	fun addRole(userId: Long, role: Role): Int

	/** 移除角色，返回删除的行数。 */
	fun removeRole(userId: Long, role: Role): Int
}
