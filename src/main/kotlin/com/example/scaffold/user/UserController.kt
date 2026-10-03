package com.example.scaffold.user

import com.example.scaffold.platform.Page
import com.example.scaffold.platform.userId
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val service: UserService) {

	/** 注册，无需登录（见 SecurityConfig）。 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun create(@Valid @RequestBody req: CreateUserRequest): UserRecord =
		service.create(req.name, req.email, req.password)

	/** 当前登录用户。 */
	@GetMapping("/me")
	fun me(@AuthenticationPrincipal jwt: Jwt): UserRecord =
		service.get(jwt.userId)

	@GetMapping("/{id}")
	fun get(@PathVariable id: Long): UserRecord =
		service.get(id)

	@GetMapping
	fun search(
		@RequestParam keyword: String?,
		@RequestParam(defaultValue = "1") @Min(1) @Max(100_000) page: Int,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
	): Page<UserRecord> =
		service.search(keyword, page, size)

	@PatchMapping("/{id}")
	fun rename(@PathVariable id: Long, @Valid @RequestBody req: RenameUserRequest): UserRecord =
		service.rename(id, req.name)

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(@PathVariable id: Long) {
		service.delete(id)
	}
}

data class CreateUserRequest(
	@NotBlank @Size(max = 50) val name: String,
	@NotBlank @Email val email: String,
	@Size(min = 8, max = 72) val password: String,
) {
	/** BCrypt 最多处理 72 字节，超出时会抛异常；@Size 按字符数算，中文一个字占 3 字节，需另外按字节校验。 */
	@get:AssertTrue
	val passwordWithinBcryptLimit: Boolean
		get() = password.toByteArray().size <= 72
}

data class RenameUserRequest(
	@NotBlank @Size(max = 50) val name: String,
)
