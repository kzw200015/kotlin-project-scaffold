package com.example.scaffold.user

import com.example.scaffold.TestcontainersConfiguration
import com.example.scaffold.platform.AppException
import com.example.scaffold.platform.PasswordEncoderConfig
import com.example.scaffold.platform.Tx
import org.junit.jupiter.api.Test
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@MybatisTest
@Import(UserService::class, Tx::class, PasswordEncoderConfig::class, TestcontainersConfiguration::class)
class UserServiceTests(
	@Autowired private val service: UserService,
	@Autowired private val mapper: UserMapper,
	@Autowired private val passwordEncoder: PasswordEncoder,
) {

	@Test
	fun 注册时只保存密码哈希() {
		service.create("alice", "alice@example.com", "password1")

		val hash = mapper.findCredentialByEmail("alice@example.com")?.passwordHash
		assertNotEquals("password1", hash)
		assertTrue(passwordEncoder.matches("password1", hash))
	}

	@Test
	fun 邮箱重复时抛出40901() {
		service.create("alice", "alice@example.com", "password1")

		val e = assertFailsWith<AppException> { service.create("alice2", "alice@example.com", "password1") }
		assertEquals(40901, e.code)
	}

	@Test
	fun 搜索返回当前页数据和总数() {
		listOf("bob", "carol", "dave").forEach { service.create(it, "$it@example.com", "password1") }

		val page = service.search(keyword = null, page = 2, size = 2)

		assertEquals(listOf("dave"), page.items.map { it.name })
		assertEquals(3, page.total)
	}

	@Test
	fun 搜索关键字中的通配符按字面匹配() {
		service.create("bob", "bob@example.com", "password1")
		service.create("a_b", "a_b@example.com", "password1")

		assertEquals(listOf("a_b"), service.search(keyword = "_", page = 1, size = 10).items.map { it.name })
		assertEquals(0, service.search(keyword = "%", page = 1, size = 10).total)
	}

	@Test
	fun 改名后返回最新数据() {
		val id = service.create("bob", "bob@example.com", "password1").id

		assertEquals("robert", service.rename(id, "robert").name)
	}

	@Test
	fun 用户不存在时抛出40401() {
		assertEquals(40401, assertFailsWith<AppException> { service.get(999) }.code)
		assertEquals(40401, assertFailsWith<AppException> { service.rename(999, "x") }.code)
		assertEquals(40401, assertFailsWith<AppException> { service.delete(999) }.code)
	}
}
