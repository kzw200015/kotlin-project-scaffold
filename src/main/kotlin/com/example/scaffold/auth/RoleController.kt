package com.example.scaffold.auth

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 管理用户的角色，需要管理员（见 SecurityConfig）。 */
@RestController
@RequestMapping("/api/v1/users/{id}/roles")
class RoleController(private val service: RoleService) {

	/** 授予角色；已有该角色时不变。 */
	@PutMapping("/{role}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun grant(@PathVariable id: Long, @PathVariable role: Role) {
		service.grant(id, role)
	}

	/** 移除角色；没有该角色时不变。 */
	@DeleteMapping("/{role}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun revoke(@PathVariable id: Long, @PathVariable role: Role) {
		service.revoke(id, role)
	}
}
