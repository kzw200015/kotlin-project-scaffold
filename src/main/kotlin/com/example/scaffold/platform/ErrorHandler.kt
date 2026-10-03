package com.example.scaffold.platform

import jakarta.servlet.DispatcherType
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.webmvc.error.ErrorController
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.net.URI

/**
 * 把错误统一写成 Problem Details（RFC 9457，`application/problem+json`）：
 * - [AppException]：HTTP 状态码由业务码推出，`detail` 为 message，扩展字段 `code` 为业务码；
 * - Spring MVC 的请求错误（参数校验、404、405、415 等）：沿用 Spring 生成的 Problem Details，`detail` 为通用描述；
 * - 其他异常：只有状态码和 `title`（请求错误为 4xx，其余为 500），不暴露细节。
 *
 * 两个入口：Controller 抛出的异常由 [handle] 处理；没经过 Controller 的错误由 Tomcat 转发到 `/error`，由 [error] 处理。
 */
@RestControllerAdvice
@RestController
class ErrorHandler : ErrorController {
	private val log = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler(Exception::class)
	fun handle(e: Exception, request: HttpServletRequest): ResponseEntity<ProblemDetail>? {
		val problem = when (e) {
			is AppException -> ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.status), e.message).apply { setProperty("code", e.code) }
			// 客户端已断开连接，无需也无法响应
			is AsyncRequestNotUsableException -> return null
			// Spring MVC 的请求错误（参数校验、缺少参数、404、405、415 等）实现了 ErrorResponse，自带状态码和 Problem Details
			is ErrorResponse -> e.body
			// 未实现 ErrorResponse 的请求错误：参数类型不匹配（如 /users/abc）、请求体不是合法 JSON 或缺少非空字段
			is MethodArgumentTypeMismatchException, is HttpMessageNotReadableException -> ProblemDetail.forStatus(400)
			else -> ProblemDetail.forStatus(500)
		}
		// 从 /error 转发过来的异常 Tomcat 已经记过日志
		if (problem.status >= 500 && request.dispatcherType != DispatcherType.ERROR) {
			log.error("request failed: {} {}", request.method, request.requestURI, e)
		}
		// 保留 Spring 设置的响应头，如 405 的 Allow、415 的 Accept
		return respond(problem, (e as? ErrorResponse)?.headers ?: HttpHeaders(), request)
	}

	/**
	 * 接管 Spring Boot 默认的 `/error`，处理没经过 Controller 的错误：Filter 抛出的异常、调用 `sendError` 返回的错误、
	 * Tomcat 直接返回的错误等。有异常时按 [handle] 的规则处理，只有状态码时原样返回该状态码。
	 */
	@RequestMapping("\${spring.web.error.path:\${error.path:/error}}")
	fun error(request: HttpServletRequest): ResponseEntity<ProblemDetail>? {
		var e = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) as Throwable?
		// Filter 抛出的 checked 异常通常包在 ServletException 中
		while (e is ServletException && e.cause != null) e = e.cause
		if (e is Exception) return handle(e, request)

		val status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as Int? ?: 500
		return respond(ProblemDetail.forStatus(status), HttpHeaders(), request)
	}

	private fun respond(problem: ProblemDetail, headers: HttpHeaders, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
		// instance 为空时 Spring 会填入当前请求路径，转发到 /error 后就成了 /error，这里改为原始请求路径
		if (request.dispatcherType == DispatcherType.ERROR) {
			(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) as String?)?.let { problem.instance = URI.create(it) }
		}
		return ResponseEntity.status(problem.status).headers(headers).body(problem)
	}
}
