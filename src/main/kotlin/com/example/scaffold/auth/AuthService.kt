package com.example.scaffold.auth

import com.example.scaffold.platform.JwtProperties
import com.example.scaffold.user.UserMapper
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AuthService(
	private val mapper: UserMapper,
	private val passwordEncoder: PasswordEncoder,
	private val jwtEncoder: JwtEncoder,
	private val jwt: JwtProperties,
) {
	/** 用户不存在时拿它比对，使耗时与密码错误时一致。 */
	private val dummyHash = checkNotNull(passwordEncoder.encode("dummy-password"))

	/** 校验邮箱和密码，签发 token。 */
	fun login(email: String, password: String): AccessToken {
		val credential = mapper.findCredentialByEmail(email)
		val hash = credential?.passwordHash
		// 用户不存在或未设置密码时也做一次哈希比对，避免通过响应时间判断邮箱是否已注册
		val matched = passwordEncoder.matches(password, hash ?: dummyHash)
		if (credential == null || hash == null || !matched) AuthErrors.badCredentials()
		return issue(credential.id)
	}

	private fun issue(userId: Long): AccessToken {
		val now = Instant.now()
		val expiresAt = now + jwt.ttl
		val claims = JwtClaimsSet.builder()
			.subject(userId.toString())
			.issuedAt(now)
			.expiresAt(expiresAt)
			.build()
		val header = JwsHeader.with(MacAlgorithm.HS256).build()
		val token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
		return AccessToken(token.tokenValue, expiresAt)
	}
}

data class AccessToken(val token: String, val expiresAt: Instant)
