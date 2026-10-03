package com.example.scaffold.auth

import com.example.scaffold.PostgresContainer
import com.example.scaffold.RedisContainer
import com.example.scaffold.delete
import com.example.scaffold.forbidden
import com.example.scaffold.get
import com.example.scaffold.unauthorized
import com.example.scaffold.user.UserMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 注册、登录、带 token 访问、注销的完整流程，会话存在 Redis 中。401 / 403 的响应体要经过 /error 转发才会写出，
 * MockMvc 不会转发，需要启动真实服务器验证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Sql(statements = ["DELETE FROM users"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AuthTests(
	@Autowired private val client: RestTestClient,
	@Autowired private val users: UserMapper,
	@Autowired private val roles: RoleMapper,
	@Autowired private val redis: StringRedisTemplate,
	@Autowired private val props: AuthProperties,
) : PostgresContainer, RedisContainer {
	/** 数据库由 @Sql 清理；会话也一并清掉，避免残留的 token 影响其他测试。 */
	@AfterEach
	fun clearSessions() {
		redis.delete(redis.keys("auth:token:*"))
	}

	@Test
	@DisplayName("注册登录后可以访问需要登录的接口")
	fun registeredUserCanAccessProtectedEndpoint() {
		client.register("alice@example.com")

		client.get("/api/v1/users/me", client.token("alice@example.com"))
			.expectStatus().isOk()
			.expectBody().jsonPath("$.email").isEqualTo("alice@example.com")
	}

	@Test
	@DisplayName("登录时邮箱不区分大小写")
	fun loginEmailIsCaseInsensitive() {
		client.register("alice@example.com")

		client.login(" Alice@Example.COM ", PASSWORD).expectStatus().isOk()
	}

	@Test
	@DisplayName("密码错误和邮箱未注册都返回 40101")
	fun wrongPasswordAndUnknownEmailBothReturn40101() {
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
	@DisplayName("未携带 token 返回 401 统一格式")
	fun missingTokenReturns401ProblemDetail() {
		client.get("/api/v1/users/1", token = null)
			.expectStatus().isUnauthorized()
			.expectHeader().valueMatches("WWW-Authenticate", "Bearer.*")
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody().json(unauthorized("/api/v1/users/1"), JsonCompareMode.STRICT)
	}

	@Test
	@DisplayName("无效 token 返回 401")
	fun invalidTokenReturns401() {
		// 过期由 Redis 删除 key 实现，与不存在的 token 走同一分支；有效期是否设置见 redisStoresTokenHashWithTtl
		client.get("/api/v1/users/me", "unknown-token")
			.expectStatus().isUnauthorized()
			.expectHeader().valueMatches("WWW-Authenticate", ".*invalid_token.*")
			.expectBody().json(unauthorized("/api/v1/users/me"), JsonCompareMode.STRICT)
	}

	@Test
	@DisplayName("普通用户删除用户返回 403，授予管理员后同一个 token 立即生效")
	fun grantingAdminTakesEffectOnExistingToken() {
		val aliceId = client.register("alice@example.com")
		val bobId = client.register("bob@example.com")
		val token = client.token("alice@example.com")

		// URL 级规则拒绝：ExceptionTranslationFilter 调用 AccessDeniedHandler
		client.delete("/api/v1/users/$bobId", token)
			.expectStatus().isForbidden()
			.expectHeader().valueMatches("WWW-Authenticate", ".*insufficient_scope.*")
			.expectBody().json(forbidden("/api/v1/users/$bobId"), JsonCompareMode.STRICT)

		roles.add(aliceId, Role.ADMIN)
		// 不用重新登录：角色在每个请求时从数据库加载
		client.delete("/api/v1/users/$bobId", token).expectStatus().isNoContent()
	}

	@Test
	@DisplayName("普通用户改别人的名字返回 403")
	fun renamingOtherUserReturns403() {
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
	@DisplayName("Redis 中只存 token 的哈希并按有效期自动过期")
	fun redisStoresTokenHashWithTtl() {
		val id = client.register("alice@example.com")
		val issuedAfter = Instant.now()
		val (token, expiresAt) = client.login("alice@example.com", PASSWORD)
			.expectStatus().isOk().returnResult<AccessToken>().responseBody!!

		assertTrue(redis.keys("*").none { token in it })
		assertEquals(id.toString(), redis.opsForValue().get(tokenKey(token)))
		assertTrue(redis.getExpire(tokenKey(token)) in 1..props.ttl.seconds)
		// 返回给客户端的过期时间与 Redis 中一致：签发时刻 + 有效期
		assertTrue(expiresAt in (issuedAfter + props.ttl)..(Instant.now() + props.ttl))
	}

	@Test
	@DisplayName("注销后 token 立即失效")
	fun logoutRevokesTokenImmediately() {
		client.register("alice@example.com")
		val token = client.token("alice@example.com")

		client.logout(token).expectStatus().isNoContent()

		client.get("/api/v1/users/me", token).expectStatus().isUnauthorized()
	}

	@Test
	@DisplayName("用户删除后 token 失效")
	fun deletingUserRevokesToken() {
		val id = client.register("alice@example.com")
		val token = client.token("alice@example.com")

		users.deleteById(id)

		client.get("/api/v1/users/me", token).expectStatus().isUnauthorized()
	}

	@Test
	@DisplayName("健康检查无需登录")
	fun healthCheckIsPublic() {
		// 200 表示数据库和 Redis 都可用，任一不可用时为 503
		client.get("/actuator/health", token = null).expectStatus().isOk()
	}
}
