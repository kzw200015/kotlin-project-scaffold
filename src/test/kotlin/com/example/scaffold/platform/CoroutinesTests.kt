package com.example.scaffold.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class CoroutinesTests {

	@Test
	@DisplayName("任务运行在虚拟线程上")
	fun runsOnVirtualThread() = runBlocking {
		val thread = withContext(Dispatchers.Virtual) { Thread.currentThread() }

		assertTrue(thread.isVirtual)
		assertTrue(thread.name.startsWith("coroutine-vt-"))
	}
}
