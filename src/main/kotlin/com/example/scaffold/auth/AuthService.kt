package com.example.scaffold.auth

import com.example.scaffold.user.UserMapper
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
	private val mapper: UserMapper,
	private val passwordEncoder: PasswordEncoder,
	private val tokenService: TokenService,
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
		return tokenService.issue(credential.id)
	}

	/** 注销：吊销当前 token。 */
	fun logout(token: String) {
		tokenService.revoke(token)
	}
}
