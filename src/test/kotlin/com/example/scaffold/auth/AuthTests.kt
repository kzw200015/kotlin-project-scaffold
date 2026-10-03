package com.example.scaffold.auth

import com.example.scaffold.TestcontainersConfiguration
import com.example.scaffold.platform.Role
import com.example.scaffold.user.UserMapper
import com.example.scaffold.user.UserRecord
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.time.Duration
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

/** 注册、登录、带 token 访问的完整流程。401 / 403 的响应体要经过 /error 转发才会写出，MockMvc 不会转发，需要启动真实服务器验证。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration::class)
@Sql(statements = ["DELETE FROM users"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AuthTests(
	@Autowired private val client: RestTestClient,
	@Autowired private val jwtEncoder: JwtEncoder,
	@Autowired private val mapper: UserMapper,
) {
	@Test
	fun 注册登录后可以访问需要登录的接口() {
		register("alice@example.com", "password1")

		get("/api/v1/users/me", token("alice@example.com", "password1"))
			.expectStatus().isOk()
			.expectBody().jsonPath("$.email").isEqualTo("alice@example.com")
	}

	@Test
	fun 密码错误和邮箱未注册都返回40101() {
		register("alice@example.com", "password1")

		// 超过 BCrypt 72 字节上限的密码同样按密码错误处理
		val attempts = listOf("alice@example.com" to "wrong-password", "alice@example.com" to "密".repeat(30), "nobody@example.com" to "password1")
		for ((email, password) in attempts) {
			login(email, password)
				.expectStatus().isUnauthorized()
				.expectBody().json(
					"""{"title":"Unauthorized","status":401,"detail":"invalid email or password","instance":"/api/v1/auth/login","code":40101}""",
					JsonCompareMode.STRICT,
				)
		}
	}

	@Test
	fun 未携带token返回401统一格式() {
		get("/api/v1/users/1", token = null)
			.expectStatus().isUnauthorized()
			.expectHeader().valueMatches("WWW-Authenticate", "Bearer.*")
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody().json(unauthorized("/api/v1/users/1"), JsonCompareMode.STRICT)
	}

	@Test
	fun 过期或伪造的token返回401() {
		val expired = sign(jwtEncoder, expiresAt = Instant.now() - Duration.ofMinutes(5))
		val otherKey = SecretKeySpec("another-secret-at-least-32-bytes-long".toByteArray(), "HmacSHA256")
		val forged = sign(NimbusJwtEncoder.withSecretKey(otherKey).algorithm(MacAlgorithm.HS256).build(), Instant.now() + Duration.ofHours(1))

		for (token in listOf(expired, forged, "not-a-jwt")) {
			get("/api/v1/users/me", token)
				.expectStatus().isUnauthorized()
				.expectHeader().valueMatches("WWW-Authenticate", ".*invalid_token.*")
				.expectBody().json(unauthorized("/api/v1/users/me"), JsonCompareMode.STRICT)
		}
	}

	@Test
	fun 登录时角色写入token_管理员可以删除用户_普通用户返回403() {
		val aliceId = register("alice@example.com", "password1")
		val bobId = register("bob@example.com", "password1")
		mapper.addRole(aliceId, Role.ADMIN)
		val admin = token("alice@example.com", "password1")
		val user = token("bob@example.com", "password1")

		// URL 级规则拒绝：ExceptionTranslationFilter 调用 AccessDeniedHandler
		delete("/api/v1/users/$aliceId", user)
			.expectStatus().isForbidden()
			.expectHeader().valueMatches("WWW-Authenticate", ".*insufficient_scope.*")
			.expectBody().json(forbidden("/api/v1/users/$aliceId"), JsonCompareMode.STRICT)
		delete("/api/v1/users/$bobId", admin).expectStatus().isNoContent()
	}

	@Test
	fun 普通用户改别人的名字返回403() {
		val aliceId = register("alice@example.com", "password1")
		register("bob@example.com", "password1")

		// 方法级规则（@PreAuthorize）拒绝：经 ErrorHandler 重新抛出，交给 ExceptionTranslationFilter，响应与 URL 级规则一致
		client.patch().uri("/api/v1/users/$aliceId")
			.headers { it.setBearerAuth(token("bob@example.com", "password1")) }
			.contentType(MediaType.APPLICATION_JSON).body("""{"name": "robert"}""")
			.exchange()
			.expectStatus().isForbidden()
			.expectHeader().valueMatches("WWW-Authenticate", ".*insufficient_scope.*")
			.expectBody().json(forbidden("/api/v1/users/$aliceId"), JsonCompareMode.STRICT)
	}

	@Test
	fun 健康检查无需登录() {
		get("/actuator/health", token = null).expectStatus().isOk()
	}

	/** Spring Security 拒绝请求时没有业务码，只有状态码和 title。 */
	private fun unauthorized(path: String) = """{"title":"Unauthorized","status":401,"instance":"$path"}"""

	private fun forbidden(path: String) = """{"title":"Forbidden","status":403,"instance":"$path"}"""

	private fun sign(encoder: JwtEncoder, expiresAt: Instant): String {
		val claims = JwtClaimsSet.builder().subject("1").issuedAt(expiresAt - Duration.ofHours(1)).expiresAt(expiresAt).build()
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
	}

	/** 注册并返回用户 id，用户名取邮箱 @ 之前的部分。 */
	private fun register(email: String, password: String): Long =
		post("/api/v1/users", """{"name": "${email.substringBefore('@')}", "email": "$email", "password": "$password"}""")
			.expectStatus().isCreated()
			.returnResult<UserRecord>().responseBody!!.id

	private fun login(email: String, password: String) =
		post("/api/v1/auth/login", """{"email": "$email", "password": "$password"}""")

	private fun token(email: String, password: String): String =
		login(email, password).expectStatus().isOk().returnResult<AccessToken>().responseBody!!.token

	private fun post(path: String, body: String) =
		client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).exchange()

	private fun get(path: String, token: String?) =
		client.get().uri(path).headers { if (token != null) it.setBearerAuth(token) }.exchange()

	private fun delete(path: String, token: String) =
		client.delete().uri(path).headers { it.setBearerAuth(token) }.exchange()
}
