package com.example.scaffold.user

import org.apache.ibatis.annotations.AutomapConstructor
import java.time.OffsetDateTime

/**
 * users 表的一行。id、createdAt 由数据库生成，插入后经 generated keys 回填，因此声明为可空 var。
 */
data class UserRecord @AutomapConstructor constructor(
	var id: Long? = null,
	val name: String,
	val email: String,
	var createdAt: OffsetDateTime? = null,
)
