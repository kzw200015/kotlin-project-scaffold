package com.example.scaffold.platform

import com.example.scaffold.PostgresContainer
import com.example.scaffold.user.UserMapper
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@MybatisTest
@Import(Tx::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // 关闭测试默认的回滚事务，才能观察到真实的提交与回滚
@Sql(statements = ["DELETE FROM users"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TxTests(
	@Autowired private val tx: Tx,
	@Autowired private val mapper: UserMapper,
) : PostgresContainer {

	@Test
	@DisplayName("写事务提交并返回代码块结果")
	fun writeCommitsAndReturnsBlockResult() {
		val inserted = tx.write { mapper.insert("alice", "alice@example.com", "hash") }

		assertEquals(inserted, mapper.findById(inserted.id))
		assertEquals(1, userCount())
	}

	@Test
	@DisplayName("抛出异常时回滚并原样抛出")
	fun exceptionRollsBackAndIsRethrown() {
		assertFailsWith<IllegalStateException> {
			tx.write {
				mapper.insert("alice", "alice@example.com", "hash")
				error("boom")
			}
		}
		assertFailsWith<IOException> {
			tx.write {
				mapper.insert("bob", "bob@example.com", "hash")
				throw IOException("checked")
			}
		}

		assertEquals(0, userCount())
	}

	@Test
	@DisplayName("调用 setRollbackOnly 时不抛异常也回滚")
	fun setRollbackOnlyRollsBackWithoutException() {
		tx.write { status ->
			mapper.insert("alice", "alice@example.com", "hash")
			status.setRollbackOnly()
		}

		assertEquals(0, userCount())
	}

	@Test
	@DisplayName("只读事务拒绝写操作")
	fun readOnlyRejectsWrites() {
		assertFailsWith<DataAccessException> {
			tx.read { mapper.insert("alice", "alice@example.com", "hash") }
		}
	}

	private fun userCount() = mapper.count(keyword = null)
}
