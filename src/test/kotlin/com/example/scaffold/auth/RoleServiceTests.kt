package com.example.scaffold.auth

import com.example.scaffold.PostgresContainer
import com.example.scaffold.platform.AppException
import com.example.scaffold.user.UserMapper
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

	// 违反外键约束后测试所在的事务即中止，之后不能再执行 SQL，所以授予和移除分成两个测试
	@Test
	fun 授予角色时用户不存在抛出40401() {
		assertEquals(40401, assertFailsWith<AppException> { service.grant(999, Role.ADMIN) }.code)
	}

	@Test
	fun 移除角色时用户不存在抛出40401() {
		assertEquals(40401, assertFailsWith<AppException> { service.revoke(999, Role.ADMIN) }.code)
	}

	@Test
	fun 授予和移除角色可以重复执行() {
		val id = users.insert("alice", "alice@example.com", "hash").id

		repeat(2) { service.grant(id, Role.ADMIN) }
		assertEquals(listOf(Role.ADMIN), mapper.findByUserId(id))

		repeat(2) { service.revoke(id, Role.ADMIN) }
		assertEquals(listOf(null), mapper.findByUserId(id))
	}
}
