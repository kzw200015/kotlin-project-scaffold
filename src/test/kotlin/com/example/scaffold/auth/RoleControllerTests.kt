package com.example.scaffold.auth

import com.example.scaffold.asAdmin
import com.example.scaffold.asUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.put

@WebMvcTest(RoleController::class)
@Import(SecurityConfig::class)
class RoleControllerTests(@Autowired private val mvc: MockMvc) {

	@MockitoBean
	private lateinit var service: RoleService

	/** SecurityConfig 需要它，认证结果由 asUser() / asAdmin() 直接放入，不会被调用。 */
	@MockitoBean
	private lateinit var tokenIntrospector: OpaqueTokenIntrospector

	@Test
	@DisplayName("管理员授予和移除角色")
	fun adminGrantsAndRevokesRoles() {
		mvc.put("/api/v1/users/2/roles/ADMIN") { with(asAdmin()) }.andExpect { status { isNoContent() } }
		mvc.delete("/api/v1/users/2/roles/ADMIN") { with(asAdmin()) }.andExpect { status { isNoContent() } }
		// 不存在的角色名
		mvc.put("/api/v1/users/2/roles/ROOT") { with(asAdmin()) }.andExpect { status { isBadRequest() } }

		verify(service).grant(2, Role.ADMIN)
		verify(service).revoke(2, Role.ADMIN)
	}

	@Test
	@DisplayName("普通用户不能管理角色")
	fun regularUserCannotManageRoles() {
		mvc.put("/api/v1/users/2/roles/ADMIN") { with(asUser(1)) }.andExpect { status { isForbidden() } }
		mvc.delete("/api/v1/users/2/roles/ADMIN") { with(asUser(1)) }.andExpect { status { isForbidden() } }

		verifyNoInteractions(service)
	}
}
