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
	@DisplayName("每个角色都能写入数据库")
	fun everyRoleCanBeStored() {
		// Role 枚举与 user_roles.role 的 CHECK 约束不一致时这里会失败
		val id = users.insert("alice", "alice@example.com", "hash").id

		Role.entries.forEach { mapper.add(id, it) }

		assertEquals(Role.entries.sortedBy { it.name }, mapper.findByUserId(id))
	}
}
