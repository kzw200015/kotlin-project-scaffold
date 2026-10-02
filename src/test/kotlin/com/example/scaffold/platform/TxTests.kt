package com.example.scaffold.platform

import com.example.scaffold.TestcontainersConfiguration
import com.example.scaffold.user.UserMapper
import com.example.scaffold.user.UserRecord
import com.example.scaffold.user.count
import com.example.scaffold.user.delete
import com.example.scaffold.user.insert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@MybatisTest
@Import(Tx::class, TestcontainersConfiguration::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // 关闭测试默认的回滚事务，才能观察到真实的提交与回滚
class TxTests(
	@Autowired private val tx: Tx,
	@Autowired private val mapper: UserMapper,
) {

	@AfterEach
	fun cleanUp() {
		mapper.delete { allRows() }
	}

	@Test
	fun `write commits and returns the block result`() {
		val inserted = tx.write { mapper.insert(UserRecord(name = "alice", email = "alice@example.com")) }

		assertEquals(1, inserted)
		assertEquals(1, userCount())
	}

	@Test
	fun `exception rolls back and is rethrown as is`() {
		assertFailsWith<IllegalStateException> {
			tx.write {
				mapper.insert(UserRecord(name = "alice", email = "alice@example.com"))
				error("boom")
			}
		}
		assertFailsWith<IOException> {
			tx.write {
				mapper.insert(UserRecord(name = "bob", email = "bob@example.com"))
				throw IOException("checked")
			}
		}

		assertEquals(0, userCount())
	}

	@Test
	fun `setRollbackOnly rolls back without exception`() {
		tx.write { status ->
			mapper.insert(UserRecord(name = "alice", email = "alice@example.com"))
			status.setRollbackOnly()
		}

		assertEquals(0, userCount())
	}

	@Test
	fun `read rejects writes`() {
		assertFailsWith<DataAccessException> {
			tx.read { mapper.insert(UserRecord(name = "alice", email = "alice@example.com")) }
		}
	}

	private fun userCount() = mapper.count { allRows() }
}
