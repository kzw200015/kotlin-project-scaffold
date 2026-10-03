package com.example.scaffold

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals

/**
 * 校验 db/schema.sql 与 Flyway 迁移后的真实表结构一致；表结构变更后执行 `./gradlew updateSchema` 重新生成。
 */
@MybatisTest
class SchemaSnapshotTests : PostgresContainer {

	private val snapshot = Path.of("db/schema.sql")

	@Test
	@DisplayName("表结构快照与迁移结果一致")
	fun schemaSnapshotMatchesMigrations() {
		val actual = dumpSchema()

		if (System.getProperty("schema.update") == "true") {
			snapshot.parent.createDirectories()
			snapshot.writeText(actual)
			return
		}

		check(snapshot.exists()) { "缺少 $snapshot，执行 ./gradlew updateSchema 生成" }
		assertEquals(snapshot.readText(), actual, "$snapshot 已过期，执行 ./gradlew updateSchema 重新生成")
	}

	/** 用容器内的 pg_dump 导出表结构（版本与数据库一致），去掉会话设置、注释等与结构无关的行。 */
	private fun dumpSchema(): String {
		val postgres = PostgresContainer.postgres
		val result = postgres.execInContainer(
			"pg_dump", "--username", postgres.username, "--dbname", postgres.databaseName,
			"--schema-only", "--no-owner", "--no-privileges", "--exclude-table=flyway_schema_history",
		)
		check(result.exitCode == 0) { "pg_dump 失败：${result.stderr}" }

		val body = result.stdout.lineSequence()
			// \restrict 等 psql 元命令每次导出都带随机值，必须去掉
			.filterNot { it.startsWith("--") || it.startsWith("SET ") || it.startsWith("SELECT pg_catalog.") || it.startsWith("\\") }
			.joinToString("\n")
			.replace(Regex("\n{3,}"), "\n\n")
			.trim()
		return "-- 由 SchemaSnapshotTests 生成，勿手改；表结构变更后执行 ./gradlew updateSchema 更新\n\n$body\n"
	}
}
