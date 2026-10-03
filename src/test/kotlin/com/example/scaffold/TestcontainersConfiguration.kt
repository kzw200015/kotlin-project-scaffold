package com.example.scaffold

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration
class TestcontainersConfiguration {

	/**
	 * 所有测试上下文共用一个容器：`@Import` 组合不同的测试类各有自己的上下文，每次 new 会各启动一个 PostgreSQL。
	 * 任一上下文关闭时容器都会被停止，所以不要用 `@DirtiesContext`。
	 */
	@Bean
	@ServiceConnection
	fun postgresContainer(): PostgreSQLContainer = postgres

	companion object {
		private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.6-alpine"))
	}
}
