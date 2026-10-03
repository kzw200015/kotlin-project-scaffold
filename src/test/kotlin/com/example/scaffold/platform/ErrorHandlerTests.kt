package com.example.scaffold.platform

import com.example.scaffold.PostgresContainer
import com.example.scaffold.get
import com.example.scaffold.unauthorized
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.Ordered
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

/** 没经过 Controller 的错误（Filter 中抛出异常、调用 sendError）由 Tomcat 转发到 /error，MockMvc 不会转发，需要启动真实服务器验证。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(ErrorHandlerTests.FailingFilterConfig::class)
class ErrorHandlerTests(@Autowired private val client: RestTestClient) : PostgresContainer {
	@Test
	fun Filter抛出业务异常按业务码返回() {
		client.get("/fail/app", token = null)
			.expectStatus().isUnauthorized()
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
			// instance 为原始请求路径，而不是转发后的 /error
			.expectBody().json(
				"""{"title":"Unauthorized","status":401,"detail":"unauthorized","instance":"/fail/app","code":40101}""",
				JsonCompareMode.STRICT,
			)
	}

	@Test
	fun Filter抛出未预期异常返回500且不泄露细节() {
		// checked 异常会被 Tomcat 包在 ServletException 中
		for (path in listOf("/fail/unexpected", "/fail/checked")) {
			client.get(path, token = null)
				.expectStatus().isEqualTo(500)
				.expectBody().json("""{"title":"Internal Server Error","status":500,"instance":"$path"}""", JsonCompareMode.STRICT)
		}
	}

	@Test
	fun Filter调用sendError时保留状态码和响应头() {
		client.get("/fail/send-error", token = null)
			.expectStatus().isUnauthorized()
			.expectHeader().valueEquals("WWW-Authenticate", "Bearer")
			.expectBody().json(unauthorized("/fail/send-error"), JsonCompareMode.STRICT)
	}

	@Test
	fun Filter调用sendError500时只返回状态码() {
		client.get("/fail/send-error-500", token = null)
			.expectStatus().isEqualTo(500)
			.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody().json("""{"title":"Internal Server Error","status":500,"instance":"/fail/send-error-500"}""", JsonCompareMode.STRICT)
	}

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
					"/fail/send-error-500" -> response.sendError(500)
					else -> chain.doFilter(request, response)
				}
			}
		}).apply { order = Ordered.HIGHEST_PRECEDENCE }
	}
}
