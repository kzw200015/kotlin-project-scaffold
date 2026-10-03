package com.example.scaffold.auth

import com.example.scaffold.user.UserRecord
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult

// AuthTests / OpaqueTokenAuthTests 共用的请求辅助函数

/** 测试用户统一的密码。 */
internal const val PASSWORD = "password1"

/** 注册并返回用户 id，用户名取邮箱 @ 之前的部分。 */
internal fun RestTestClient.register(email: String, password: String = PASSWORD): Long =
	postJson("/api/v1/users", """{"name": "${email.substringBefore('@')}", "email": "$email", "password": "$password"}""")
		.expectStatus().isCreated()
		.returnResult<UserRecord>().responseBody!!.id

internal fun RestTestClient.login(email: String, password: String = PASSWORD) =
	postJson("/api/v1/auth/login", """{"email": "$email", "password": "$password"}""")

internal fun RestTestClient.token(email: String, password: String = PASSWORD): String =
	login(email, password).expectStatus().isOk().returnResult<AccessToken>().responseBody!!.token

internal fun RestTestClient.logout(token: String) =
	post().uri("/api/v1/auth/logout").headers { it.setBearerAuth(token) }.exchange()

internal fun RestTestClient.postJson(path: String, body: String) =
	post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).exchange()

internal fun RestTestClient.get(path: String, token: String?) =
	get().uri(path).headers { if (token != null) it.setBearerAuth(token) }.exchange()

internal fun RestTestClient.delete(path: String, token: String) =
	delete().uri(path).headers { it.setBearerAuth(token) }.exchange()

/** Spring Security 拒绝请求时没有业务码，只有状态码和 title。 */
internal fun unauthorized(path: String) = """{"title":"Unauthorized","status":401,"instance":"$path"}"""

internal fun forbidden(path: String) = """{"title":"Forbidden","status":403,"instance":"$path"}"""
