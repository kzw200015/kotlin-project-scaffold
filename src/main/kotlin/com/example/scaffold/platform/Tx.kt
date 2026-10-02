package com.example.scaffold.platform

import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.lang.reflect.UndeclaredThrowableException

/**
 * 显式事务，替代 `@Transactional`：事务边界写在代码里，不依赖 AOP 代理。
 *
 * 两个方法都使用 REQUIRED 传播：当前线程已有事务时加入，否则新开。block 抛出任何异常都会回滚并原样抛出；
 * 不抛异常也想回滚时调用 `status.setRollbackOnly()`。事务绑定在当前线程，block 内切换线程（如 `async`）的代码不在事务中。
 */
@Component
class Tx(transactionManager: PlatformTransactionManager) {
	private val readWrite = TransactionTemplate(transactionManager)
	private val readOnly = TransactionTemplate(transactionManager).apply { isReadOnly = true }

	/** 在读写事务中执行 [block]。 */
	fun <T> write(block: (TransactionStatus) -> T): T = execute(readWrite, block)

	/** 在只读事务中执行 [block]，写操作会被数据库拒绝。只读不代表一致快照：默认 READ COMMITTED 下，多条查询仍可能看到不同时刻的数据。 */
	fun <T> read(block: (TransactionStatus) -> T): T = execute(readOnly, block)

	private fun <T> execute(template: TransactionTemplate, block: (TransactionStatus) -> T): T =
		try {
			template.execute(block)
		} catch (e: UndeclaredThrowableException) {
			// TransactionTemplate 会把 checked 异常包一层，Kotlin 不区分 checked 异常，这里还原为原始异常
			throw e.cause ?: e
		}
}
