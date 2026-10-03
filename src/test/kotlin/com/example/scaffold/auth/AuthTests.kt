package com.example.scaffold.auth

import com.example.scaffold.TestcontainersConfiguration
import com.example.scaffold.platform.ApiResponse
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

/** 注册、登录、带 token 访问的完整流程。401 的响应体要经过 /error 转发才会写出，MockMvc 不会转发，需要启动真实服务器验证。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration::class)
@Sql(statements = ["DELETE FROM users"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AuthTests(
	@Autowired private val client: RestTestClient,
	@Autowired private val jwtEncoder: JwtEncoder,
) {
	private val unauthorized = """{"code":null,"message":null,"data":null}"""

	@Test
	fun 注册登录后可以访问需要登录的接口() {
		register("alice@example.com", "password1")
		val token = login("alice@example.com", "password1").expectStatus().isOk()
			.returnResult<ApiResponse<AccessToken>>().responseBody!!.data!!.token

		get("/api/v1/users/me", token)
			.expectStatus().isOk()
			.expectBody().jsonPath("$.data.email").isEqualTo("alice@example.com")
	}

	@Test
	fun 密码错误和邮箱未注册都返回40101() {
		register("alice@example.com", "password1")

		// 超过 BCrypt 72 字节上限的密码同样按密码错误处理
		val attempts = listOf("alice@example.com" to "wrong-password", "alice@example.com" to "密".repeat(30), "nobody@example.com" to "password1")
		for ((email, password) in attempts) {
			login(email, password)
				.expectStatus().isUnauthorized()
				.expectBody().json("""{"code":40101,"message":"invalid email or password","data":null}""", JsonCompareMode.STRICT)
		}
	}

	@Test
	fun 未携带token返回401统一格式() {
		get("/api/v1/users/1", token = null)
			.expectStatus().isUnauthorized()
			.expectHeader().valueMatches("WWW-Authenticate", "Bearer.*")
			.expectBody().json(unauthorized, JsonCompareMode.STRICT)
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
				.expectBody().json(unauthorized, JsonCompareMode.STRICT)
		}
	}

	@Test
	fun 健康检查无需登录() {
		get("/actuator/health", token = null).expectStatus().isOk()
	}

	private fun sign(encoder: JwtEncoder, expiresAt: Instant): String {
		val claims = JwtClaimsSet.builder().subject("1").issuedAt(expiresAt - Duration.ofHours(1)).expiresAt(expiresAt).build()
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
	}

	private fun register(email: String, password: String) {
		post("/api/v1/users", """{"name": "alice", "email": "$email", "password": "$password"}""").expectStatus().isCreated()
	}

	private fun login(email: String, password: String) =
		post("/api/v1/auth/login", """{"email": "$email", "password": "$password"}""")

	private fun post(path: String, body: String) =
		client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).exchange()

	private fun get(path: String, token: String?) =
		client.get().uri(path).headers { if (token != null) it.setBearerAuth(token) }.exchange()
}
