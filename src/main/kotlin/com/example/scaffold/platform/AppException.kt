package com.example.scaffold.platform

/**
 * 业务异常，由 [ErrorHandler] 写成 Problem Details：[code] 放在扩展字段 `code` 中，message 作为 `detail` 原样返回给客户端（不要放内部细节）。
 *
 * [code] 为 5 位业务码，前三位即 HTTP 状态码（如 40401 → 404），后两位区分同一状态下的不同错误。
 * 各领域把自己的错误集中定义为直接抛出异常的函数，如 `UserErrors.notFound(id)`。
 */
class AppException(val code: Int, override val message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
	init {
		require(code in 40000..59999) { "业务码必须是 5 位且前三位为 4xx/5xx 状态码: $code" }
	}

	/** 业务码前三位即 HTTP 状态码。 */
	val status: Int
		get() = code / 100
}
