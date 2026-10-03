package com.example.scaffold

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/** 共用的 Redis 容器（自动设置 spring.data.redis.*），生命周期同 [PostgresContainer]。只有经过 TokenService 的测试需要实现它。 */
interface RedisContainer {
	companion object {
		@JvmField
		@ServiceConnection
		val redis = GenericContainer<Nothing>(DockerImageName.parse("redis:8-alpine")).apply { withExposedPorts(6379) }
	}
}
