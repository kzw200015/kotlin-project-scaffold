package com.example.scaffold.user

import com.example.scaffold.PostgresContainer
import com.example.scaffold.platform.AppException
import com.example.scaffold.platform.PasswordEncoderConfig
import com.example.scaffold.platform.Tx
import org.junit.jupiter.api.DisplayName
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
@Import(UserService::class, Tx::class, PasswordEncoderConfig::class)
class UserServiceTests(
	@Autowired private val service: UserService,
	@Autowired private val mapper: UserMapper,
	@Autowired private val passwordEncoder: PasswordEncoder,
) : PostgresContainer {

	@Test
	@DisplayName("注册时只保存密码哈希")
	fun createStoresOnlyPasswordHash() {
		service.create("alice", "alice@example.com", "password1")

		val hash = mapper.findCredentialByEmail("alice@example.com")?.passwordHash
		assertNotEquals("password1", hash)
		assertTrue(passwordEncoder.matches("password1", hash))
	}

	@Test
	@DisplayName("邮箱重复时抛出 40901，不区分大小写")
	fun duplicateEmailThrows40901IgnoringCase() {
		assertEquals("alice@example.com", service.create("alice", " Alice@Example.COM ", "password1").email)

		val e = assertFailsWith<AppException> { service.create("alice2", "ALICE@example.com", "password1") }
		assertEquals(40901, e.code)
	}

	@Test
	@DisplayName("搜索关键字中的通配符按字面匹配")
	fun searchMatchesWildcardsLiterally() {
		service.create("bob", "bob@example.com", "password1")
		service.create("a_b", "a_b@example.com", "password1")

		assertEquals(listOf("a_b"), service.search(keyword = "_", page = 1, size = 10).items.map { it.name })
		assertEquals(0, service.search(keyword = "%", page = 1, size = 10).total)
	}
}
