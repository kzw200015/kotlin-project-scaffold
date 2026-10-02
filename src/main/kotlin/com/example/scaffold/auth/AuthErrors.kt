package com.example.scaffold.auth

import com.example.scaffold.platform.AppException

/** auth 领域的业务错误。 */
object AuthErrors {
	/** 不区分邮箱不存在和密码错误，避免据此探测邮箱是否已注册。 */
	fun badCredentials(): Nothing = throw AppException(40101, "invalid email or password")
}
