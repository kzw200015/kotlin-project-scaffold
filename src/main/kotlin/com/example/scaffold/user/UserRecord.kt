package com.example.scaffold.user

import java.time.OffsetDateTime

/** users 表的一行，不含密码哈希。 */
data class UserRecord(
	val id: Long,
	val name: String,
	val email: String,
	val createdAt: OffsetDateTime,
)

/** 登录校验用的凭证，只在鉴权中使用，不要返回给客户端。 */
data class UserCredential(
	val id: Long,
	val passwordHash: String,
)
