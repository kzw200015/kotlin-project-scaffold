package com.example.scaffold.auth

import com.example.scaffold.postJson
import com.example.scaffold.user.UserRecord
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult

// 注册、登录、注销的请求辅助函数，通用的请求辅助函数见 RestTestClientSupport.kt

/** 测试用户统一的密码。 */
internal const val PASSWORD = "password1"

/** 注册并返回用户 id，用户名取邮箱 @ 之前的部分。 */
internal fun RestTestClient.register(email: String): Long =
	postJson("/api/v1/users", """{"name": "${email.substringBefore('@')}", "email": "$email", "password": "$PASSWORD"}""")
		.expectStatus().isCreated()
		.returnResult<UserRecord>().responseBody!!.id

internal fun RestTestClient.login(email: String, password: String) =
	postJson("/api/v1/auth/login", """{"email": "$email", "password": "$password"}""")

internal fun RestTestClient.token(email: String): String =
	login(email, PASSWORD).expectStatus().isOk().returnResult<AccessToken>().responseBody!!.token

internal fun RestTestClient.logout(token: String) =
	post().uri("/api/v1/auth/logout").headers { it.setBearerAuth(token) }.exchange()
