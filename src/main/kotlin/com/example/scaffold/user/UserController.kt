package com.example.scaffold.user

import com.example.scaffold.platform.ApiResponse
import com.example.scaffold.platform.Page
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
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

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun create(@Valid @RequestBody req: CreateUserRequest): ApiResponse<UserRecord> =
		ApiResponse.ok(service.create(req.name, req.email))

	@GetMapping("/{id}")
	fun get(@PathVariable id: Long): ApiResponse<UserRecord> =
		ApiResponse.ok(service.get(id))

	@GetMapping
	fun search(
		@RequestParam keyword: String?,
		@RequestParam(defaultValue = "1") @Min(1) @Max(100_000) page: Int,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
	): ApiResponse<Page<UserRecord>> =
		ApiResponse.ok(service.search(keyword, page, size))

	@PatchMapping("/{id}")
	fun rename(@PathVariable id: Long, @Valid @RequestBody req: RenameUserRequest): ApiResponse<UserRecord> =
		ApiResponse.ok(service.rename(id, req.name))

	@DeleteMapping("/{id}")
	fun delete(@PathVariable id: Long): ApiResponse<Nothing> {
		service.delete(id)
		return ApiResponse.ok()
	}
}

data class CreateUserRequest(
	@NotBlank @Size(max = 50) val name: String,
	@NotBlank @Email val email: String,
)

data class RenameUserRequest(
	@NotBlank @Size(max = 50) val name: String,
)
