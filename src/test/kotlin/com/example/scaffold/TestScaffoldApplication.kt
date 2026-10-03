package com.example.scaffold

import org.springframework.boot.fromApplication
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.boot.with

/** `./gradlew bootTestRun` 的入口：用 Testcontainers 临时 PostgreSQL 和 Redis 启动应用。 */
fun main(args: Array<String>) {
	fromApplication<ScaffoldApplication>().with(ContainersConfiguration::class).run(*args)
}

/** 复用测试的容器声明。与测试不同，这里把容器注册成 bean，随应用关闭而停止。 */
@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers(PostgresContainer::class, RedisContainer::class)
class ContainersConfiguration
