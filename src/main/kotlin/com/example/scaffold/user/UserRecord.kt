package com.example.scaffold.user

import java.time.OffsetDateTime

/** users 表的一行。 */
data class UserRecord(
	val id: Long,
	val name: String,
	val email: String,
	val createdAt: OffsetDateTime,
)
