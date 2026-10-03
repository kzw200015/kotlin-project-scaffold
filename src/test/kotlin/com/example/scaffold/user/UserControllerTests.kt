package com.example.scaffold.user

import com.example.scaffold.asAdmin
import com.example.scaffold.asUser
import com.example.scaffold.auth.SecurityConfig
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.opaqueToken
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.request.RequestPostProcessor
import java.time.OffsetDateTime

@WebMvcTest(UserController::class)
@Import(SecurityConfig::class) // @WebMvcTest 不扫描 @Configuration，不导入时用的是 Spring Boot 默认的安全配置
class UserControllerTests(@Autowired private val mvc: MockMvc) {

	@MockitoBean
	private lateinit var service: UserService

	/** SecurityConfig 需要它；请求的认证结果由 opaqueToken() 直接放入，不经过 token 校验，所以不会被调用。 */
	@MockitoBean
	private lateinit var tokenIntrospector: OpaqueTokenIntrospector

	private val alice = UserRecord(1, "alice", "alice@example.com", OffsetDateTime.parse("2026-01-01T00:00:00Z"))

	@Test
	@DisplayName("只能改自己的名字，管理员可以改任何人")
	fun renameOnlySelfUnlessAdmin() {
		whenever(service.rename(1, "robert")).thenReturn(alice)

		rename(1, asUser(2)).andExpect { status { isForbidden() } }
		rename(1, asUser(1)).andExpect { status { isOk() } }
		rename(1, asAdmin()).andExpect { status { isOk() } }
	}

	@Test
	@DisplayName("请求参数不合法返回 400 且没有业务码")
	fun invalidRequestReturns400WithoutCode() {
		val responses = listOf(
			postUser("""{"name": "", "email": "not-an-email", "password": "password1"}"""),
			// 缺少字段
			postUser("""{"name": "alice"}"""),
			// 30 个汉字：30 个字符，90 字节，超过 BCrypt 72 字节上限
			postUser("""{"name": "alice", "email": "alice@example.com", "password": "${"密".repeat(30)}"}"""),
			mvc.get("/api/v1/users?size=1000") { with(opaqueToken()) },
			mvc.get("/api/v1/users/abc") { with(opaqueToken()) },
		)
		for (response in responses) {
			response.andExpect {
				status { isBadRequest() }
				content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
				jsonPath("$.status") { value(400) }
				jsonPath("$.code") { doesNotExist() }
			}
		}
	}

	@Test
	@DisplayName("路径、方法或 Content-Type 不匹配时返回对应状态码")
	fun unmatchedPathMethodOrContentTypeReturnsCorrespondingStatus() {
		mvc.get("/api/v1/nope") { with(opaqueToken()) }.andExpect {
			status { isNotFound() }
		}
		mvc.put("/api/v1/users/1") { with(opaqueToken()) }.andExpect {
			status { isMethodNotAllowed() }
			header { string("Allow", containsString("GET")) }
		}
		mvc.post("/api/v1/users") {
			contentType = MediaType.TEXT_PLAIN
			content = "alice"
		}.andExpect {
			status { isUnsupportedMediaType() }
		}
	}

	private fun postUser(json: String) = mvc.post("/api/v1/users") {
		contentType = MediaType.APPLICATION_JSON
		content = json
	}

	private fun rename(id: Long, auth: RequestPostProcessor) = mvc.patch("/api/v1/users/$id") {
		with(auth)
		contentType = MediaType.APPLICATION_JSON
		content = """{"name": "robert"}"""
	}
}
