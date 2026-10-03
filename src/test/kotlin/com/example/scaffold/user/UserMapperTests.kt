package com.example.scaffold.user

import com.example.scaffold.TestcontainersConfiguration
import com.example.scaffold.platform.Role
import org.junit.jupiter.api.Test
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertNull

@MybatisTest
@Import(TestcontainersConfiguration::class)
class UserMapperTests(@Autowired private val mapper: UserMapper) {

	@Test
	fun 插入后返回数据库生成的新行() {
		val row = mapper.insert("alice", "alice@example.com", "hash")

		assertEquals("alice", row.name)
		assertEquals("alice@example.com", row.email)
		assertEquals(row, mapper.findById(row.id))
	}

	@Test
	fun 按邮箱查询登录凭证() {
		val id = mapper.insert("alice", "alice@example.com", "{bcrypt}xxx").id

		assertEquals(UserCredential(id, "{bcrypt}xxx"), mapper.findCredentialByEmail("alice@example.com"))
		assertNull(mapper.findCredentialByEmail("nobody@example.com"))
	}

	@Test
	fun 搜索按关键字过滤并分页() {
		listOf("bob", "carol", "dave").forEach { mapper.insert(it, "$it@example.com", "hash") }

		assertEquals(listOf("bob", "carol"), mapper.search(keyword = null, limit = 2, offset = 0).map { it.name })
		assertEquals(listOf("dave"), mapper.search(keyword = null, limit = 2, offset = 2).map { it.name })
		assertEquals(listOf("carol"), mapper.search(keyword = "CAR", limit = 10, offset = 0).map { it.name })
		assertEquals(3, mapper.count(keyword = ""))
		assertEquals(1, mapper.count(keyword = "dave@"))
	}

	@Test
	fun 按id更新和删除() {
		val id = mapper.insert("bob", "bob@example.com", "hash").id

		assertEquals(1, mapper.updateName(id, "robert"))
		assertEquals("robert", mapper.findById(id)?.name)

		assertEquals(1, mapper.deleteById(id))
		assertNull(mapper.findById(id))
	}

	@Test
	fun 授予和移除角色() {
		val id = mapper.insert("alice", "alice@example.com", "hash").id
		assertEquals(emptyList(), mapper.findRoles(id))

		assertEquals(1, mapper.addRole(id, Role.ADMIN))
		// 重复授予不报错
		assertEquals(0, mapper.addRole(id, Role.ADMIN))
		assertEquals(listOf(Role.ADMIN), mapper.findRoles(id))

		assertEquals(1, mapper.removeRole(id, Role.ADMIN))
		assertEquals(emptyList(), mapper.findRoles(id))
	}

	@Test
	fun 删除用户时一并删除角色() {
		val id = mapper.insert("alice", "alice@example.com", "hash").id
		mapper.addRole(id, Role.ADMIN)

		assertEquals(1, mapper.deleteById(id))
		assertEquals(emptyList(), mapper.findRoles(id))
	}
}
