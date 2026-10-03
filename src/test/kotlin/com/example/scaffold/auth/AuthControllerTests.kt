package com.example.scaffold.auth

import org.junit.jupiter.api.Test
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class)
class AuthControllerTests(@Autowired private val mvc: MockMvc) {

	@MockitoBean
	private lateinit var service: AuthService

	/** SecurityConfig 需要它，登录接口无需 token，不会被调用。 */
	@MockitoBean
	private lateinit var tokenIntrospector: OpaqueTokenIntrospector

	@Test
	fun 登录时邮箱或密码为空返回400() {
		for (body in listOf("""{"email": "", "password": "password1"}""", """{"email": "alice@example.com", "password": " "}""")) {
			mvc.post("/api/v1/auth/login") {
				contentType = MediaType.APPLICATION_JSON
				content = body
			}.andExpect {
				status { isBadRequest() }
				jsonPath("$.code") { doesNotExist() }
			}
		}

		verifyNoInteractions(service)
	}
}
