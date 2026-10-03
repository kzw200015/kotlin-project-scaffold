package com.example.scaffold.auth

import com.example.scaffold.user.UserMapper
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.HexFormat

/**
 * 登录会话：token 是 32 字节随机数，本身不含任何信息；Redis 中保存 `auth:token:{token 的 SHA-256}` → 用户 id，
 * 过期时间为 `app.auth.ttl`，到期由 Redis 自动删除。
 *
 * 同时是 Spring Security 的 [OpaqueTokenIntrospector]（见 SecurityConfig）：每个请求查 Redis 得到用户 id，
 * 再从数据库加载用户和角色，所以改角色、删除用户立即生效。
 */
@Service
class TokenService(
	private val redis: StringRedisTemplate,
	private val users: UserMapper,
	private val props: AuthProperties,
) : OpaqueTokenIntrospector {
	private val keyGenerator = Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 32)

	/** 登录成功后签发 token。Redis 中只存 token 的哈希，Redis 数据泄露也拿不到可用的 token。 */
	fun issue(userId: Long): AccessToken {
		val token = keyGenerator.generateKey()
		redis.opsForValue().set(tokenKey(token), userId.toString(), props.ttl)
		return AccessToken(token, Instant.now() + props.ttl)
	}

	/** 吊销 token（注销），之后用它的请求返回 401。 */
	fun revoke(token: String) {
		redis.delete(tokenKey(token))
	}

	/** 校验请求携带的 token。查不到（不存在、已过期、已注销）或用户已删除时抛出 BadOpaqueTokenException，由入口点返回 401 invalid_token。 */
	override fun introspect(token: String): OAuth2AuthenticatedPrincipal {
		val userId = redis.opsForValue().get(tokenKey(token))?.toLong()
		if (userId == null || users.findById(userId) == null) throw BadOpaqueTokenException("invalid or expired token")
		val authorities = users.findRoles(userId).map { SimpleGrantedAuthority("ROLE_${it.name}") }
		// sub 即 authentication.name
		return DefaultOAuth2AuthenticatedPrincipal(mapOf(OAuth2TokenIntrospectionClaimNames.SUB to userId.toString()), authorities)
	}
}

data class AccessToken(val token: String, val expiresAt: Instant)

@ConfigurationProperties("app.auth")
data class AuthProperties(
	/** token 有效期，即 Redis 中会话的过期时间。 */
	val ttl: Duration = Duration.ofHours(2),
)

/** token 在 Redis 中的 key。token 是高熵随机数，用 SHA-256 即可，不需要 BCrypt 这类慢哈希。 */
internal fun tokenKey(token: String): String =
	"auth:token:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))
