package com.example.scaffold.user

import com.example.scaffold.PostgresContainer
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

@MybatisTest
class UserMapperTests(@Autowired private val mapper: UserMapper) : PostgresContainer {

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
}
