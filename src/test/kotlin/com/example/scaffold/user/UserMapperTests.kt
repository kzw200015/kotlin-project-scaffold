package com.example.scaffold.user

import com.example.scaffold.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertNull

@MybatisTest
@Import(TestcontainersConfiguration::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserMapperTests(@Autowired private val mapper: UserMapper) {

	@Test
	fun `insert returns the new row`() {
		val row = mapper.insert("alice", "alice@example.com")

		assertEquals("alice", row.name)
		assertEquals("alice@example.com", row.email)
		assertEquals(row, mapper.findById(row.id))
	}

	@Test
	fun `search filters by keyword and pages`() {
		listOf("bob", "carol", "dave").forEach { mapper.insert(it, "$it@example.com") }

		assertEquals(listOf("bob", "carol"), mapper.search(keyword = null, limit = 2, offset = 0).map { it.name })
		assertEquals(listOf("dave"), mapper.search(keyword = null, limit = 2, offset = 2).map { it.name })
		assertEquals(listOf("carol"), mapper.search(keyword = "CAR", limit = 10, offset = 0).map { it.name })
		assertEquals(3, mapper.count(keyword = ""))
		assertEquals(1, mapper.count(keyword = "dave@"))
	}

	@Test
	fun `update and delete by id`() {
		val id = mapper.insert("bob", "bob@example.com").id

		assertEquals(1, mapper.updateName(id, "robert"))
		assertEquals("robert", mapper.findById(id)?.name)

		assertEquals(1, mapper.deleteById(id))
		assertNull(mapper.findById(id))
	}
}
