package com.example.scaffold

import org.springframework.boot.fromApplication
import org.springframework.boot.with

/** `./gradlew bootTestRun` 的入口：用 Testcontainers 临时 PostgreSQL 和 Redis 启动应用。 */
fun main(args: Array<String>) {
	fromApplication<ScaffoldApplication>().with(TestcontainersConfiguration::class, RedisTestcontainersConfiguration::class).run(*args)
}
