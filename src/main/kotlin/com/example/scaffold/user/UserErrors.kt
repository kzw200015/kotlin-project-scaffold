package com.example.scaffold.user

import com.example.scaffold.platform.AppException

/** user 领域的业务错误。函数直接抛出异常（返回 Nothing），调用处写作 `?: UserErrors.notFound(id)`。 */
object UserErrors {
	fun notFound(id: Long, cause: Throwable? = null): Nothing = throw AppException(40401, "user not found: id=$id", cause)

	fun emailTaken(email: String, cause: Throwable? = null): Nothing =
		throw AppException(40901, "email already registered: $email", cause)
}
