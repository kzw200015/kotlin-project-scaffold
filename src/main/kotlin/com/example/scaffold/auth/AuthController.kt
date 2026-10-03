package com.example.scaffold.auth

import com.example.scaffold.platform.tokenValue
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val service: AuthService) {

	/** 登录，无需登录（见 SecurityConfig）。之后的请求带上 `Authorization: Bearer <token>`。 */
	@PostMapping("/login")
	fun login(@Valid @RequestBody req: LoginRequest): AccessToken =
		service.login(req.email, req.password)

	/** 注销当前 token，需要登录。 */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun logout(authentication: Authentication) {
		service.logout(authentication.tokenValue)
	}
}

data class LoginRequest(
	@NotBlank val email: String,
	@NotBlank val password: String,
)
