package com.example.scaffold.user

import com.example.scaffold.platform.SecurityConfig
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.OffsetDateTime

@WebMvcTest(UserController::class)
@Import(SecurityConfig::class) // @WebMvcTest 不扫描 @Configuration，不导入时用的是 Spring Boot 默认的安全配置
class UserControllerTests(@Autowired private val mvc: MockMvc) {

	@MockitoBean
	private lateinit var service: UserService

	private val alice = UserRecord(1, "alice", "alice@example.com", OffsetDateTime.parse("2026-01-01T00:00:00Z"))

	@Test
	fun 创建成功返回201和统一响应() {
		whenever(service.create("alice", "alice@example.com", "password1")).thenReturn(alice)

		// 注册无需登录
		mvc.post("/api/v1/users") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"name": "alice", "email": "alice@example.com", "password": "password1"}"""
		}.andExpect {
			status { isCreated() }
			jsonPath("$.code") { value(0) }
			jsonPath("$.message") { value("ok") }
			jsonPath("$.data.id") { value(1) }
			jsonPath("$.data.createdAt") { value("2026-01-01T00:00:00Z") }
		}
	}

	@Test
	fun 密码超过72字节返回400() {
		mvc.post("/api/v1/users") {
			contentType = MediaType.APPLICATION_JSON
			// 30 个汉字：30 个字符，90 字节
			content = """{"name": "alice", "email": "alice@example.com", "password": "${"密".repeat(30)}"}"""
		}.andExpect {
			status { isBadRequest() }
		}
	}

	@Test
	fun 未登录访问返回401并带上WWW_Authenticate头() {
		mvc.get("/api/v1/users/1").andExpect {
			status { isUnauthorized() }
			header { string("WWW-Authenticate", startsWith("Bearer")) }
		}
	}

	@Test
	fun 获取当前登录用户() {
		whenever(service.get(1)).thenReturn(alice)

		mvc.get("/api/v1/users/me") { with(jwt().jwt { it.subject("1") }) }.andExpect {
			status { isOk() }
			jsonPath("$.data.email") { value("alice@example.com") }
		}
	}

	@Test
	fun 请求体校验失败返回400且不带业务码() {
		mvc.post("/api/v1/users") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"name": "", "email": "not-an-email"}"""
		}.andExpect {
			status { isBadRequest() }
			jsonPath("$.code") { value(nullValue()) }
			jsonPath("$.message") { value(nullValue()) }
			jsonPath("$.data") { value(nullValue()) }
		}
	}

	@Test
	fun 请求体缺少字段返回400() {
		mvc.post("/api/v1/users") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"name": "alice"}"""
		}.andExpect {
			status { isBadRequest() }
		}
	}

	@Test
	fun 查询参数校验失败返回400() {
		mvc.get("/api/v1/users?size=1000") { with(jwt()) }.andExpect {
			status { isBadRequest() }
		}
	}

	@Test
	fun 路径参数类型错误返回400() {
		mvc.get("/api/v1/users/abc") { with(jwt()) }.andExpect {
			status { isBadRequest() }
		}
	}

	@Test
	fun 业务异常按业务码返回() {
		whenever(service.get(9)).thenAnswer { UserErrors.notFound(9) }

		mvc.get("/api/v1/users/9") { with(jwt()) }.andExpect {
			status { isNotFound() }
			jsonPath("$.code") { value(40401) }
			jsonPath("$.message") { value("user not found: id=9") }
		}
	}

	@Test
	fun 未预期异常返回500且不泄露细节() {
		whenever(service.get(1)).thenThrow(IllegalStateException("password=secret"))

		mvc.get("/api/v1/users/1") { with(jwt()) }.andExpect {
			status { isInternalServerError() }
			jsonPath("$.code") { value(nullValue()) }
			jsonPath("$.message") { value(nullValue()) }
		}
	}

	@Test
	fun 未知路径返回404() {
		mvc.get("/api/v1/nope") { with(jwt()) }.andExpect {
			status { isNotFound() }
		}
	}

	@Test
	fun 不支持的方法返回405并带上Allow头() {
		mvc.put("/api/v1/users/1") { with(jwt()) }.andExpect {
			status { isMethodNotAllowed() }
			header { string("Allow", containsString("GET")) }
		}
	}

	@Test
	fun 不支持的ContentType返回415() {
		mvc.post("/api/v1/users") {
			contentType = MediaType.TEXT_PLAIN
			content = "alice"
		}.andExpect {
			status { isUnsupportedMediaType() }
		}
	}

	@Test
	fun 删除成功时data为null() {
		mvc.delete("/api/v1/users/1") { with(jwt()) }.andExpect {
			status { isOk() }
			jsonPath("$.code") { value(0) }
			jsonPath("$.data") { value(nullValue()) }
		}
	}
}
