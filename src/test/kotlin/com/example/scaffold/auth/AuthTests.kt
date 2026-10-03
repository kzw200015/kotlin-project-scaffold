package com.example.scaffold.auth

import com.example.scaffold.RedisTestcontainersConfiguration
import com.example.scaffold.TestcontainersConfiguration
import com.example.scaffold.platform.Role
import com.example.scaffold.user.UserMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.client.RestTestClient
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 注册、登录、带 token 访问、注销的完整流程，会话存在 Redis 中。401 / 403 的响应体要经过 /error 转发才会写出，
 * MockMvc 不会转发，需要启动真实服务器验证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration::class, RedisTestcontainersConfiguration::class)
@Sql(statements = ["DELETE FROM users"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AuthTests(
	@Autowired private val client: RestTestClient,
	@Autowired private val users: UserMapper,
	@Autowired private val redis: StringRedisTemplate,
) {
	@Test
	fun 注册登录后可以访问需要登录的接口() {
		client.register("alice@example.com")

		client.get("/api/v1/users/me", client.token("alice@example.com"))
			.expectStatus().isOk()
			.expectBody().jsonPath("$.email").isEqualTo("alice@example.com")
	}

	@Test
	fun 密码错误和邮箱未注册都返回40101() {
		client.register("alice@example.com")

		// 超过 BCrypt 72 字节上限的密码同样按密码错误处理
		val attempts = listOf("alice@example.com" to "wrong-password", "alice@example.com" to "密".repeat(30), "nobody@example.com" to PASSWORD)
		for ((email, password) in attempts) {
			client.login(email, password)
				.expectStatus().isUnauthorized()
				.expectBody().json(
					"""{"title":"Unauthorized","status":401,"detail":"invalid email or password","instance":"/api/v1/auth/login","code":40101}""",
					JsonCompareMode.STRICT,
				)
		}
	}

	@Test
	fun 未携带token返回401统一格式() {
		client.get("/api/v1/users/1", token = null)
			.expectStatus().isUnauthorized()
			.expectHeader().valueMatches("WWW-Authenticate", "Bearer.*")
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody().json(unauthorized("/api/v1/users/1"), JsonCompareMode.STRICT)
	}

	@Test
	fun 过期或不存在的token返回401() {
		val id = client.register("alice@example.com")
		redis.opsForValue().set(tokenKey("expired-token"), id.toString(), Duration.ofMillis(1))
		Thread.sleep(10)

		for (token in listOf("expired-token", "unknown-token")) {
			client.get("/api/v1/users/me", token)
				.expectStatus().isUnauthorized()
				.expectHeader().valueMatches("WWW-Authenticate", ".*invalid_token.*")
				.expectBody().json(unauthorized("/api/v1/users/me"), JsonCompareMode.STRICT)
		}
	}

	@Test
	fun 普通用户删除用户返回403_授予管理员后同一个token立即生效() {
		val aliceId = client.register("alice@example.com")
		val bobId = client.register("bob@example.com")
		val token = client.token("alice@example.com")

		// URL 级规则拒绝：ExceptionTranslationFilter 调用 AccessDeniedHandler
		client.delete("/api/v1/users/$bobId", token)
			.expectStatus().isForbidden()
			.expectHeader().valueMatches("WWW-Authenticate", ".*insufficient_scope.*")
			.expectBody().json(forbidden("/api/v1/users/$bobId"), JsonCompareMode.STRICT)

		users.addRole(aliceId, Role.ADMIN)
		// 不用重新登录：角色在每个请求时从数据库加载
		client.delete("/api/v1/users/$bobId", token).expectStatus().isNoContent()
	}

	@Test
	fun 普通用户改别人的名字返回403() {
		val aliceId = client.register("alice@example.com")
		client.register("bob@example.com")

		// 方法级规则（@PreAuthorize）拒绝：经 ErrorHandler 重新抛出，交给 ExceptionTranslationFilter，响应与 URL 级规则一致
		client.patch().uri("/api/v1/users/$aliceId")
			.headers { it.setBearerAuth(client.token("bob@example.com")) }
			.contentType(MediaType.APPLICATION_JSON).body("""{"name": "robert"}""")
			.exchange()
			.expectStatus().isForbidden()
			.expectHeader().valueMatches("WWW-Authenticate", ".*insufficient_scope.*")
			.expectBody().json(forbidden("/api/v1/users/$aliceId"), JsonCompareMode.STRICT)
	}

	@Test
	fun Redis中只存token的哈希并按有效期自动过期() {
		val id = client.register("alice@example.com")
		val token = client.token("alice@example.com")

		assertNull(redis.opsForValue().get("auth:token:$token"))
		assertEquals(id.toString(), redis.opsForValue().get(tokenKey(token)))
		// app.auth.ttl 默认 2 小时
		assertTrue(redis.getExpire(tokenKey(token)) in 1..Duration.ofHours(2).seconds)
	}

	@Test
	fun 注销后token立即失效() {
		client.register("alice@example.com")
		val token = client.token("alice@example.com")

		client.logout(token).expectStatus().isNoContent()

		client.get("/api/v1/users/me", token).expectStatus().isUnauthorized()
	}

	@Test
	fun 用户删除后token失效() {
		val id = client.register("alice@example.com")
		val token = client.token("alice@example.com")

		users.deleteById(id)

		client.get("/api/v1/users/me", token).expectStatus().isUnauthorized()
	}

	@Test
	fun 健康检查无需登录() {
		// 200 表示数据库和 Redis 都可用，任一不可用时为 503
		client.get("/actuator/health", token = null).expectStatus().isOk()
	}
}
