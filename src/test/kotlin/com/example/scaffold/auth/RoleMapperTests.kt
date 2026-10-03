package com.example.scaffold.auth

import com.example.scaffold.PostgresContainer
import com.example.scaffold.user.UserMapper
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

@MybatisTest
class RoleMapperTests(
	@Autowired private val mapper: RoleMapper,
	@Autowired private val users: UserMapper,
) : PostgresContainer {

	@Test
	@DisplayName("按用户查询角色时区分用户不存在和没有角色")
	fun findByUserIdDistinguishesMissingUserFromNoRoles() {
		val id = users.insert("alice", "alice@example.com", "hash").id

		assertEquals(emptyList(), mapper.findByUserId(999))
		assertEquals(listOf(null), mapper.findByUserId(id))

		mapper.add(id, Role.ADMIN)
		assertEquals(listOf(Role.ADMIN), mapper.findByUserId(id))
	}

	@Test
	@DisplayName("授予和移除角色")
	fun addAndRemoveRole() {
		val id = users.insert("alice", "alice@example.com", "hash").id

		assertEquals(1, mapper.add(id, Role.ADMIN))
		// 重复授予不报错
		assertEquals(0, mapper.add(id, Role.ADMIN))

		assertEquals(1, mapper.remove(id, Role.ADMIN))
		assertEquals(0, mapper.remove(id, Role.ADMIN))
	}

	@Test
	@DisplayName("每个角色都能写入数据库")
	fun everyRoleCanBeStored() {
		// Role 枚举与 user_roles.role 的 CHECK 约束不一致时这里会失败
		val id = users.insert("alice", "alice@example.com", "hash").id

		Role.entries.forEach { mapper.add(id, it) }

		assertEquals(Role.entries.sortedBy { it.name }, mapper.findByUserId(id))
	}

	@Test
	@DisplayName("删除用户时一并删除角色")
	fun deletingUserDeletesRoles() {
		val id = users.insert("alice", "alice@example.com", "hash").id
		mapper.add(id, Role.ADMIN)

		assertEquals(1, users.deleteById(id))
		assertEquals(emptyList(), mapper.findByUserId(id))
	}
}
