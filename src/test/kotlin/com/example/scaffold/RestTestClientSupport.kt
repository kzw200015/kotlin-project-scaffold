package com.example.scaffold

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

// 启动真实服务器的测试（RANDOM_PORT + RestTestClient）共用的请求辅助函数

internal fun RestTestClient.postJson(path: String, body: String) =
	post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).exchange()

internal fun RestTestClient.get(path: String, token: String?) =
	get().uri(path).headers { if (token != null) it.setBearerAuth(token) }.exchange()

internal fun RestTestClient.delete(path: String, token: String) =
	delete().uri(path).headers { it.setBearerAuth(token) }.exchange()

/** Spring Security 拒绝请求时没有业务码，只有状态码和 title。 */
internal fun unauthorized(path: String) = """{"title":"Unauthorized","status":401,"instance":"$path"}"""

internal fun forbidden(path: String) = """{"title":"Forbidden","status":403,"instance":"$path"}"""
