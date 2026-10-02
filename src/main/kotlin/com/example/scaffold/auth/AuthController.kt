package com.example.scaffold.auth

import com.example.scaffold.platform.ApiResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val service: AuthService) {

	/** 登录，无需登录（见 SecurityConfig）。之后的请求带上 `Authorization: Bearer <token>`。 */
	@PostMapping("/login")
	fun login(@Valid @RequestBody req: LoginRequest): ApiResponse<AccessToken> =
		ApiResponse.ok(service.login(req.email, req.password))
}

data class LoginRequest(
	@NotBlank val email: String,
	@NotBlank val password: String,
)
