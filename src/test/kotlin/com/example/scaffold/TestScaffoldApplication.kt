package com.example.scaffold

import org.springframework.boot.fromApplication
import org.springframework.boot.with

/** `./gradlew bootTestRun` 的入口：用 Testcontainers 临时库启动应用。 */
fun main(args: Array<String>) {
	fromApplication<ScaffoldApplication>().with(TestcontainersConfiguration::class).run(*args)
}
