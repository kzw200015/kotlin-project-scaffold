package com.example.scaffold.platform

import com.example.scaffold.TestcontainersConfiguration
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.Ordered
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

/** 没经过 Controller 的错误（Filter 中抛出异常、调用 sendError）由 Tomcat 转发到 /error，MockMvc 不会转发，需要启动真实服务器验证。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, ErrorHandlerTests.FailingFilterConfig::class)
class ErrorHandlerTests(@LocalServerPort private val port: Int) {

	@Test
	fun Filter抛出业务异常按业务码返回() {
		val response = get("/fail/app")

		assertEquals(401, response.statusCode())
		assertEquals("""{"code":40101,"message":"unauthorized","data":null}""", response.body())
	}

	@Test
	fun Filter抛出未预期异常返回500且不泄露细节() {
		val response = get("/fail/unexpected")

		assertEquals(500, response.statusCode())
		assertEquals("""{"code":null,"message":null,"data":null}""", response.body())
	}

	@Test
	fun Filter抛出checked异常返回500() {
		val response = get("/fail/checked")

		assertEquals(500, response.statusCode())
		assertEquals("""{"code":null,"message":null,"data":null}""", response.body())
	}

	@Test
	fun Filter调用sendError时保留状态码和响应头() {
		val response = get("/fail/send-error")

		assertEquals(401, response.statusCode())
		assertEquals("Bearer", response.headers().firstValue("WWW-Authenticate").orElse(null))
		assertEquals("""{"code":null,"message":null,"data":null}""", response.body())
	}

	private fun get(path: String): HttpResponse<String> =
		HttpClient.newHttpClient().send(
			HttpRequest.newBuilder(URI("http://localhost:$port$path")).build(),
			HttpResponse.BodyHandlers.ofString(),
		)

	@TestConfiguration
	class FailingFilterConfig {
		// 排在 Spring Security 之前，否则请求先因未登录被拒绝
		@Bean
		fun failingFilter() = FilterRegistrationBean(object : OncePerRequestFilter() {
			override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
				when (request.requestURI) {
					"/fail/app" -> throw AppException(40101, "unauthorized")
					"/fail/unexpected" -> error("password=secret")
					"/fail/checked" -> throw IOException("disk full")
					"/fail/send-error" -> {
						response.setHeader("WWW-Authenticate", "Bearer")
						response.sendError(401)
					}
					else -> chain.doFilter(request, response)
				}
			}
		}).apply { order = Ordered.HIGHEST_PRECEDENCE }
	}
}
