package com.example.scaffold.auth

import com.example.scaffold.PostgresContainer
import com.example.scaffold.platform.AppException
import com.example.scaffold.user.UserMapper
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@MybatisTest
@Import(RoleService::class)
class RoleServiceTests(
	@Autowired private val service: RoleService,
	@Autowired private val mapper: RoleMapper,
	@Autowired private val users: UserMapper,
) : PostgresContainer {

	// 违反外键约束后测试所在的事务即中止，之后不能再执行 SQL，所以单独一个测试
	@Test
	@DisplayName("授予角色时用户不存在抛出 40401")
	fun grantToMissingUserThrows40401() {
		assertEquals(40401, assertFailsWith<AppException> { service.grant(999, Role.ADMIN) }.code)
	}

	@Test
	@DisplayName("授予和移除角色可以重复执行")
	fun grantAndRevokeAreIdempotent() {
		val id = users.insert("alice", "alice@example.com", "hash").id

		repeat(2) { service.grant(id, Role.ADMIN) }
		assertEquals(listOf(Role.ADMIN), mapper.findByUserId(id))

		repeat(2) { service.revoke(id, Role.ADMIN) }
		assertEquals(listOf(null), mapper.findByUserId(id))
	}
}
