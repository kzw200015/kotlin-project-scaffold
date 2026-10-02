package com.example.scaffold.platform

import jakarta.servlet.DispatcherType
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.webmvc.error.ErrorController
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * 把错误统一写成 [ApiResponse]：
 * - [AppException]：HTTP 状态码由业务码推出，响应体带 code 和 message；
 * - 其他异常：只用 HTTP 状态码表达错误（请求错误为 4xx，其余为 500），code 和 message 留空，不暴露细节。
 *
 * 两个入口：Controller 抛出的异常由 [handle] 处理；没经过 Controller 的错误由 Tomcat 转发到 `/error`，由 [error] 处理。
 */
@RestControllerAdvice
@RestController
class ErrorHandler : ErrorController {
	private val log = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler(Exception::class)
	fun handle(e: Exception, request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>>? {
		val status = when (e) {
			is AppException -> e.status
			// 客户端已断开连接，无需也无法响应
			is AsyncRequestNotUsableException -> return null
			// Spring MVC 的请求错误（参数校验、缺少参数、404、405、415 等）实现了 ErrorResponse，自带状态码
			is ErrorResponse -> e.statusCode.value()
			// 未实现 ErrorResponse 的请求错误：参数类型不匹配（如 /users/abc）、请求体不是合法 JSON 或缺少非空字段
			is MethodArgumentTypeMismatchException, is HttpMessageNotReadableException -> 400
			else -> 500
		}
		// 从 /error 转发过来的异常 Tomcat 已经记过日志
		if (status >= 500 && request.dispatcherType != DispatcherType.ERROR) {
			log.error("request failed: {} {}", request.method, request.requestURI, e)
		}

		val body = if (e is AppException) ApiResponse.error(e.code, e.message) else ApiResponse.error()
		// 保留 Spring 设置的响应头，如 405 的 Allow、415 的 Accept
		val headers = (e as? ErrorResponse)?.headers ?: HttpHeaders()
		return ResponseEntity.status(status).headers(headers).body(body)
	}

	/**
	 * 接管 Spring Boot 默认的 `/error`，处理没经过 Controller 的错误：Filter 抛出的异常、调用 `sendError` 返回的错误、
	 * Tomcat 直接返回的错误等。有异常时按 [handle] 的规则处理，只有状态码时原样返回该状态码。
	 */
	@RequestMapping("\${spring.web.error.path:\${error.path:/error}}")
	fun error(request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>>? {
		var e = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) as Throwable?
		// Filter 抛出的 checked 异常通常包在 ServletException 中
		while (e is ServletException && e.cause != null) e = e.cause
		if (e is Exception) return handle(e, request)

		val status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as Int? ?: 500
		return ResponseEntity.status(status).body(ApiResponse.error())
	}
}
