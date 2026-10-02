package com.example.scaffold.user

import org.mybatis.dynamic.sql.AliasableSqlTable
import org.mybatis.dynamic.sql.util.kotlin.elements.column
import java.sql.JDBCType
import java.time.OffsetDateTime

object UserDynamicSqlSupport {
	val user = User()
	val id = user.id
	val name = user.name
	val email = user.email
	val createdAt = user.createdAt

	class User : AliasableSqlTable<User>("users", ::User) {
		val id = column<Long>(name = "id", jdbcType = JDBCType.BIGINT)
		val name = column<String>(name = "name", jdbcType = JDBCType.VARCHAR)
		val email = column<String>(name = "email", jdbcType = JDBCType.VARCHAR)
		val createdAt = column<OffsetDateTime>(name = "created_at", jdbcType = JDBCType.TIMESTAMP_WITH_TIMEZONE)
	}
}
