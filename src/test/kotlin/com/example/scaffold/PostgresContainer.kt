package com.example.scaffold

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * 所有测试共用一个 PostgreSQL 容器，测试类实现该接口即可连接（自动设置 spring.datasource.*）。
 *
 * 容器声明在接口的静态字段上而不是 `@Bean`：它不是 Spring bean，任一测试上下文关闭（缓存淘汰、`@DirtiesContext`）都不会停掉它。
 * 第一次用到时启动，JVM 退出后由 Testcontainers 删除。不要加 `@Container`：JUnit 扩展会在每个测试类结束时停止它。
 */
interface PostgresContainer {
	companion object {
		@JvmField
		@ServiceConnection
		val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.6-alpine"))
	}
}
