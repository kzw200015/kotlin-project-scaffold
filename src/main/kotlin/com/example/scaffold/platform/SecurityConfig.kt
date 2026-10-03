package com.example.scaffold.platform

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler

/**
 * 鉴权：请求头 `Authorization: Bearer <token>`，token 由登录接口签发，会话存在 Redis 中（见 `auth/TokenService.kt`）。
 *
 * 除下面列出的公开接口外，其余接口都要求登录；按角色限制的接口也在下面列出，依赖方法参数的规则写在方法上（`@PreAuthorize`）。
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig {

	@Bean
	fun securityFilterChain(http: HttpSecurity, tokenIntrospector: OpaqueTokenIntrospector): SecurityFilterChain {
		// 默认的入口点（401）和拒绝处理器（403）只设置状态码和 WWW-Authenticate 头、响应体为空；
		// 再调用 sendError 转发到 /error，由 ErrorHandler 写成 Problem Details
		val bearerEntryPoint = BearerTokenAuthenticationEntryPoint()
		val entryPoint = AuthenticationEntryPoint { request, response, e ->
			bearerEntryPoint.commence(request, response, e)
			response.sendError(response.status)
		}
		val bearerDeniedHandler = BearerTokenAccessDeniedHandler()
		val deniedHandler = AccessDeniedHandler { request, response, e ->
			bearerDeniedHandler.handle(request, response, e)
			response.sendError(response.status)
		}

		http {
			authorizeHttpRequests {
				authorize(HttpMethod.POST, "/api/v1/auth/login", permitAll)
				authorize(HttpMethod.POST, "/api/v1/users", permitAll)
				authorize("/actuator/health/**", permitAll)
				// sendError 和 Filter 异常会转发到 /error，必须放行，否则响应体为空
				authorize("/error", permitAll)
				authorize(HttpMethod.DELETE, "/api/v1/users/*", hasRole(Role.ADMIN.name))
				authorize("/api/v1/users/*/roles/**", hasRole(Role.ADMIN.name))
				authorize(anyRequest, authenticated)
			}
			oauth2ResourceServer {
				// 每个请求用 tokenIntrospector 校验 token、得到用户和角色
				opaqueToken { introspector = tokenIntrospector }
				// token 校验失败时
				authenticationEntryPoint = entryPoint
			}
			// 没带 token、已登录但没有权限时，由 ExceptionTranslationFilter 调用。方法级授权（@PreAuthorize）的拒绝也经由
			// ErrorHandler 重新抛出交给它，所以 URL 级和方法级的 401/403 走同一出口
			exceptionHandling {
				authenticationEntryPoint = entryPoint
				accessDeniedHandler = deniedHandler
			}
			sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
			// token 放在请求头而不是 Cookie，浏览器不会自动携带，不存在 CSRF 问题
			csrf { disable() }
			// 不需要默认的 /logout（它基于 session）
			logout { disable() }
		}
		return http.build()
	}
}

/** 单独放一个配置类，方便 `@MybatisTest` 等切片测试只导入它。 */
@Configuration
class PasswordEncoderConfig {
	/** 默认 BCrypt。哈希带算法前缀（如 `{bcrypt}$2a$10$...`），以后换算法时旧哈希仍能校验。 */
	@Bean
	fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
}

/** 当前登录用户的 id（`name` 即用户 id，见 TokenService.introspect）。Controller 参数写 `authentication: Authentication` 即可取得。 */
val Authentication.userId: Long
	get() = name.toLong()

/** 当前请求携带的 token 原文。 */
val Authentication.tokenValue: String
	get() = (this as AbstractOAuth2TokenAuthenticationToken<*>).token.tokenValue
