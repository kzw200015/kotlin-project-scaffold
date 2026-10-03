package com.example.scaffold.user

import com.example.scaffold.PostgresContainer
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull

@MybatisTest
class UserMapperTests(@Autowired private val mapper: UserMapper) : PostgresContainer {

	@Test
	@DisplayName("插入后返回数据库生成的新行")
	fun insertReturnsGeneratedRow() {
		val row = mapper.insert("alice", "alice@example.com", "hash")

		assertEquals("alice", row.name)
		assertEquals("alice@example.com", row.email)
		assertEquals(row, mapper.findById(row.id))
	}

	@Test
	@DisplayName("按邮箱查询登录凭证")
	fun findCredentialByEmail() {
		val id = mapper.insert("alice", "alice@example.com", "{bcrypt}xxx").id

		assertEquals(UserCredential(id, "{bcrypt}xxx"), mapper.findCredentialByEmail("alice@example.com"))
		assertNull(mapper.findCredentialByEmail("nobody@example.com"))
	}

	@Test
	@DisplayName("搜索按关键字过滤并分页")
	fun searchFiltersByKeywordAndPaginates() {
		listOf("bob", "carol", "dave").forEach { mapper.insert(it, "$it@example.com", "hash") }

		assertEquals(listOf("bob", "carol"), mapper.search(keyword = null, limit = 2, offset = 0).map { it.name })
		assertEquals(listOf("dave"), mapper.search(keyword = null, limit = 2, offset = 2).map { it.name })
		assertEquals(listOf("carol"), mapper.search(keyword = "CAR", limit = 10, offset = 0).map { it.name })
		assertEquals(3, mapper.count(keyword = ""))
		assertEquals(1, mapper.count(keyword = "dave@"))
	}

	@Test
	@DisplayName("按 id 更新和删除")
	fun updateAndDeleteById() {
		val id = mapper.insert("bob", "bob@example.com", "hash").id

		assertEquals(1, mapper.updateName(id, "robert"))
		assertEquals("robert", mapper.findById(id)?.name)

		assertEquals(1, mapper.deleteById(id))
		assertNull(mapper.findById(id))
	}
}
