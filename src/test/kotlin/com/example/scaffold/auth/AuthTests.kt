package com.example.scaffold.auth

import com.example.scaffold.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.context.jdbc.Sql
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 注册、登录、带 token 访问的完整流程。401 的响应体要经过 /error 转发才会写出，MockMvc 不会转发，需要启动真实服务器验证。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@Sql(statements = ["DELETE FROM users"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AuthTests(
	@LocalServerPort private val port: Int,
	@Autowired private val jwtEncoder: JwtEncoder,
) {
	private val http = HttpClient.newHttpClient()
	private val json = JsonMapper.builder().build()
	private val unauthorized = """{"code":null,"message":null,"data":null}"""

	@Test
	fun 注册登录后可以访问需要登录的接口() {
		register("alice@example.com", "password1")
		val token = json.readTree(login("alice@example.com", "password1").body()).at("/data/token").asString()

		val response = get("/api/v1/users/me", token)

		assertEquals(200, response.statusCode())
		assertEquals("alice@example.com", json.readTree(response.body()).at("/data/email").asString())
	}

	@Test
	fun 密码错误和邮箱未注册都返回40101() {
		register("alice@example.com", "password1")

		// 超过 BCrypt 72 字节上限的密码同样按密码错误处理
		val responses = listOf("wrong-password", "密".repeat(30)).map { login("alice@example.com", it) } + login("nobody@example.com", "password1")
		for (response in responses) {
			assertEquals(401, response.statusCode())
			assertEquals("""{"code":40101,"message":"invalid email or password","data":null}""", response.body())
		}
	}

	@Test
	fun 未携带token返回401统一格式() {
		val response = get("/api/v1/users/1", token = null)

		assertEquals(401, response.statusCode())
		assertTrue(response.headers().firstValue("WWW-Authenticate").orElse("").startsWith("Bearer"))
		assertEquals(unauthorized, response.body())
	}

	@Test
	fun 过期或伪造的token返回401() {
		val expired = sign(jwtEncoder, expiresAt = Instant.now() - Duration.ofMinutes(5))
		val otherKey = SecretKeySpec("another-secret-at-least-32-bytes-long".toByteArray(), "HmacSHA256")
		val forged = sign(NimbusJwtEncoder.withSecretKey(otherKey).algorithm(MacAlgorithm.HS256).build(), Instant.now() + Duration.ofHours(1))

		for (token in listOf(expired, forged, "not-a-jwt")) {
			val response = get("/api/v1/users/me", token)
			assertEquals(401, response.statusCode())
			assertTrue(response.headers().firstValue("WWW-Authenticate").orElse("").contains("invalid_token"))
			assertEquals(unauthorized, response.body())
		}
	}

	@Test
	fun 健康检查无需登录() {
		assertEquals(200, get("/actuator/health", token = null).statusCode())
	}

	private fun sign(encoder: JwtEncoder, expiresAt: Instant): String {
		val claims = JwtClaimsSet.builder().subject("1").issuedAt(expiresAt - Duration.ofHours(1)).expiresAt(expiresAt).build()
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
	}

	private fun register(email: String, password: String) {
		val response = post("/api/v1/users", """{"name": "alice", "email": "$email", "password": "$password"}""")
		assertEquals(201, response.statusCode())
	}

	private fun login(email: String, password: String) =
		post("/api/v1/auth/login", """{"email": "$email", "password": "$password"}""")

	private fun post(path: String, body: String): HttpResponse<String> =
		send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)))

	private fun get(path: String, token: String?): HttpResponse<String> =
		send(HttpRequest.newBuilder(uri(path)).apply { if (token != null) header("Authorization", "Bearer $token") })

	private fun send(request: HttpRequest.Builder): HttpResponse<String> =
		http.send(request.build(), HttpResponse.BodyHandlers.ofString())

	private fun uri(path: String) = URI("http://localhost:$port$path")
}
