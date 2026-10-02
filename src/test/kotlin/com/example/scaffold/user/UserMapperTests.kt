package com.example.scaffold.user

import com.example.scaffold.TestcontainersConfiguration
import com.example.scaffold.user.UserDynamicSqlSupport.email
import com.example.scaffold.user.UserDynamicSqlSupport.id
import com.example.scaffold.user.UserDynamicSqlSupport.name
import org.junit.jupiter.api.Test
import org.mybatis.dynamic.sql.util.kotlin.elements.isEqualTo
import org.mybatis.dynamic.sql.util.kotlin.elements.isLike
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@MybatisTest
@Import(TestcontainersConfiguration::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserMapperTests(@Autowired private val mapper: UserMapper) {

	@Test
	fun `insert backfills generated columns`() {
		val row = UserRecord(name = "alice", email = "alice@example.com")

		assertEquals(1, mapper.insert(row))

		val id = assertNotNull(row.id)
		assertNotNull(row.createdAt)
		assertEquals(row, mapper.selectById(id))
	}

	@Test
	fun `select, update and delete with kotlin dsl`() {
		mapper.insert(UserRecord(name = "bob", email = "bob@example.com"))
		mapper.insert(UserRecord(name = "carol", email = "carol@example.com"))

		val rows = mapper.select {
			where { email isLike "%@example.com" }
			orderBy(name)
		}
		assertEquals(listOf("bob", "carol"), rows.map { it.name })

		val bobId = rows.first().id!!
		mapper.update {
			set(name) equalTo "robert"
			where { id isEqualTo bobId }
		}
		assertEquals("robert", mapper.selectById(bobId)?.name)

		assertEquals(1, mapper.delete { where { id isEqualTo bobId } })
		assertNull(mapper.selectById(bobId))
		assertEquals(1, mapper.count { allRows() })
	}
}
