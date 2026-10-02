package com.example.scaffold.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

private val virtualThreadDispatcher: CoroutineDispatcher = Executors
	.newThreadPerTaskExecutor(Thread.ofVirtual().name("coroutine-vt-", 0).factory())
	.asCoroutineDispatcher()

/**
 * 每个任务一个虚拟线程的调度器，用于在协程里执行阻塞调用（MyBatis、阻塞 HTTP 客户端等）。
 *
 * 与 [Dispatchers.IO] 不同，它没有线程数上限，实际并发度由下游资源（如数据库连接池）约束。
 * 注意：切换到该调度器后已不在调用方线程上，事务、MDC、SecurityContext 等 ThreadLocal 状态不会跟随。
 */
val Dispatchers.Virtual: CoroutineDispatcher
	get() = virtualThreadDispatcher
