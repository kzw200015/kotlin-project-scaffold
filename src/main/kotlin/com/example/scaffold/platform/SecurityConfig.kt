package com.example.scaffold.platform

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.SecurityFilterChain
import java.time.Duration
import javax.crypto.spec.SecretKeySpec

/**
 * 鉴权：无状态 JWT，请求头 `Authorization: Bearer <token>`，token 由登录接口签发（见 `auth/AuthService.kt`）。
 * 除下面列出的公开接口外，其余接口都要求登录；按角色限制的接口也在下面列出，依赖方法参数的规则写在方法上（`@PreAuthorize`）。
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig(private val jwt: JwtProperties) {

	@Bean
	fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
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
				jwt { jwtAuthenticationConverter = jwtAuthenticationConverter() }
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
			// 无状态 token 无法在服务端注销，不需要默认的 /logout
			logout { disable() }
		}
		return http.build()
	}

	@Bean
	fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey()).algorithm(MacAlgorithm.HS256).build()

	@Bean
	fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey()).macAlgorithm(MacAlgorithm.HS256).build()

	private fun secretKey() = SecretKeySpec(jwt.secret.toByteArray(), "HmacSHA256")

	/** 把 token 的 roles 声明转成权限：`["ADMIN"]` → `ROLE_ADMIN`，供 `hasRole("ADMIN")` 判断。默认读取的是 scope 声明。 */
	private fun jwtAuthenticationConverter() = JwtAuthenticationConverter().apply {
		setJwtGrantedAuthoritiesConverter(
			JwtGrantedAuthoritiesConverter().apply {
				setAuthoritiesClaimName(ROLES_CLAIM)
				setAuthorityPrefix("ROLE_")
			},
		)
	}
}

/** 单独放一个配置类，方便 `@MybatisTest` 等切片测试只导入它。 */
@Configuration
class PasswordEncoderConfig {
	/** 默认 BCrypt。哈希带算法前缀（如 `{bcrypt}$2a$10$...`），以后换算法时旧哈希仍能校验。 */
	@Bean
	fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
}

@ConfigurationProperties("app.jwt")
data class JwtProperties(
	/** HS256 签名密钥，至少 32 字节。所有实例必须一致；泄露后任何人都能伪造 token。 */
	val secret: String,
	/** token 有效期。JWT 签发后到过期前无法吊销，不宜过长。 */
	val ttl: Duration = Duration.ofHours(2),
) {
	init {
		require(secret.toByteArray().size >= 32) { "app.jwt.secret 至少 32 字节" }
	}
}

/** 当前登录用户的 id：token 的 sub 即用户 id，签发时写入。Controller 中用 `@AuthenticationPrincipal jwt: Jwt` 取得 token。 */
val Jwt.userId: Long
	get() = checkNotNull(subject) { "token 缺少 sub" }.toLong()
