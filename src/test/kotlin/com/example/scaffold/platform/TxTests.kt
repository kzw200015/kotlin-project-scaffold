package com.example.scaffold.platform

import com.example.scaffold.TestcontainersConfiguration
import com.example.scaffold.user.UserMapper
import org.junit.jupiter.api.Test
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@MybatisTest
@Import(Tx::class, TestcontainersConfiguration::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // 关闭测试默认的回滚事务，才能观察到真实的提交与回滚
@Sql(statements = ["DELETE FROM users"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TxTests(
	@Autowired private val tx: Tx,
	@Autowired private val mapper: UserMapper,
) {

	@Test
	fun `write commits and returns the block result`() {
		val inserted = tx.write { mapper.insert("alice", "alice@example.com") }

		assertEquals(inserted, mapper.findById(inserted.id))
		assertEquals(1, userCount())
	}

	@Test
	fun `exception rolls back and is rethrown as is`() {
		assertFailsWith<IllegalStateException> {
			tx.write {
				mapper.insert("alice", "alice@example.com")
				error("boom")
			}
		}
		assertFailsWith<IOException> {
			tx.write {
				mapper.insert("bob", "bob@example.com")
				throw IOException("checked")
			}
		}

		assertEquals(0, userCount())
	}

	@Test
	fun `setRollbackOnly rolls back without exception`() {
		tx.write { status ->
			mapper.insert("alice", "alice@example.com")
			status.setRollbackOnly()
		}

		assertEquals(0, userCount())
	}

	@Test
	fun `read rejects writes`() {
		assertFailsWith<DataAccessException> {
			tx.read { mapper.insert("alice", "alice@example.com") }
		}
	}

	private fun userCount() = mapper.count(keyword = null)
}
