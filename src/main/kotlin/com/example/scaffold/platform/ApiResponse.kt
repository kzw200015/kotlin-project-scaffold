package com.example.scaffold.platform

/** 所有接口统一的响应格式。 */
data class ApiResponse<T>(
	/** 业务码，成功为 0；只有 [AppException] 才有，其他错误为 null，以 HTTP 状态码为准。 */
	val code: Int?,
	/** 提示信息；只有成功和 [AppException] 才有，其他错误为 null。 */
	val message: String?,
	/** 业务数据，失败或无数据时为 null。 */
	val data: T?,
) {
	companion object {
		fun <T> ok(data: T): ApiResponse<T> = ApiResponse(0, "ok", data)

		fun ok(): ApiResponse<Nothing> = ApiResponse(0, "ok", null)

		fun error(code: Int? = null, message: String? = null): ApiResponse<Nothing> = ApiResponse(code, message, null)
	}
}

/** 分页查询结果。 */
data class Page<T>(val items: List<T>, val total: Long)
