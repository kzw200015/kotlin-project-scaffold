package com.example.scaffold.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class CoroutinesTests {

	@Test
	fun 任务运行在虚拟线程上() = runBlocking {
		val thread = withContext(Dispatchers.Virtual) { Thread.currentThread() }

		assertTrue(thread.isVirtual)
		assertTrue(thread.name.startsWith("coroutine-vt-"))
	}

	@Test
	fun 阻塞调用并发执行且没有线程数上限() = runBlocking {
		val elapsed = measureTime {
			List(1_000) { async(Dispatchers.Virtual) { Thread.sleep(500) } }.awaitAll()
		}

		assertTrue(elapsed < 5.seconds, "elapsed: $elapsed")
	}
}
