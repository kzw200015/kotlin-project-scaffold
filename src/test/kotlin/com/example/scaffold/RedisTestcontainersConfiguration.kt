package com.example.scaffold

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/** 只有走完整鉴权流程（经过 TokenService）的测试需要 Redis，单独导入，其他测试不启动这个容器。 */
@TestConfiguration
class RedisTestcontainersConfiguration {

	/** 自动设置 spring.data.redis.*。@Bean 声明的通用容器在创建前看不到镜像名，需要用 name 指明是 Redis 连接。 */
	@Bean
	@ServiceConnection("redis")
	fun redisContainer() = GenericContainer<Nothing>(DockerImageName.parse("redis:8-alpine")).apply { withExposedPorts(6379) }
}
