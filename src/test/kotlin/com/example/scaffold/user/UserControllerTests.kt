package com.example.scaffold.user

import com.example.scaffold.asAdmin
import com.example.scaffold.asUser
import com.example.scaffold.auth.SecurityConfig
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.opaqueToken
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
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
	fun 创建成功返回201和新建的用户() {
		whenever(service.create("alice", "alice@example.com", "password1")).thenReturn(alice)

		// 注册无需登录
		postUser("""{"name": "alice", "email": "alice@example.com", "password": "password1"}""").andExpect {
			status { isCreated() }
			jsonPath("$.id") { value(1) }
			jsonPath("$.createdAt") { value("2026-01-01T00:00:00Z") }
		}
	}

	@Test
	fun 获取当前登录用户() {
		whenever(service.get(1)).thenReturn(alice)

		mvc.get("/api/v1/users/me") { with(asUser(1)) }.andExpect {
			status { isOk() }
			jsonPath("$.email") { value("alice@example.com") }
		}
	}

	@Test
	fun 删除成功返回204且没有响应体() {
		mvc.delete("/api/v1/users/1") { with(asAdmin()) }.andExpect {
			status { isNoContent() }
			content { string("") }
		}

		verify(service).delete(1)
	}

	@Test
	fun 普通用户不能删除用户() {
		mvc.delete("/api/v1/users/2") { with(asUser(1)) }.andExpect { status { isForbidden() } }

		verifyNoInteractions(service)
	}

	@Test
	fun 只能改自己的名字_管理员可以改任何人() {
		whenever(service.rename(1, "robert")).thenReturn(alice)

		rename(1, asUser(2)).andExpect { status { isForbidden() } }
		rename(1, asUser(1)).andExpect { status { isOk() } }
		rename(1, asAdmin()).andExpect { status { isOk() } }
	}

	@Test
	fun 请求参数不合法返回400且没有业务码() {
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
	fun 业务异常按业务码返回() {
		whenever(service.get(9)).thenAnswer { UserErrors.notFound(9) }

		mvc.get("/api/v1/users/9") { with(opaqueToken()) }.andExpect {
			status { isNotFound() }
			content {
				contentType(MediaType.APPLICATION_PROBLEM_JSON)
				json(
					"""{"title":"Not Found","status":404,"detail":"user not found: id=9","instance":"/api/v1/users/9","code":40401}""",
					JsonCompareMode.STRICT,
				)
			}
		}
	}

	@Test
	fun 未预期异常返回500且不泄露细节() {
		whenever(service.get(1)).thenThrow(IllegalStateException("password=secret"))

		mvc.get("/api/v1/users/1") { with(opaqueToken()) }.andExpect {
			status { isInternalServerError() }
			content { json("""{"title":"Internal Server Error","status":500,"instance":"/api/v1/users/1"}""", JsonCompareMode.STRICT) }
		}
	}

	@Test
	fun 路径方法或ContentType不匹配时返回对应状态码() {
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
