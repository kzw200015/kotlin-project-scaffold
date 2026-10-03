package com.example.scaffold.user

import com.example.scaffold.platform.Page
import com.example.scaffold.platform.Role
import com.example.scaffold.platform.Tx
import com.example.scaffold.platform.escapeLike
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserService(private val tx: Tx, private val mapper: UserMapper, private val passwordEncoder: PasswordEncoder) {

	/**
	 * 单条 INSERT 本身是原子的，不需要事务。邮箱重复靠唯一约束判断，而不是先查再插（并发下会漏判）。
	 * 注意：在外层事务中调用时，PostgreSQL 唯一约束冲突会让整个事务进入中止状态，这里的捕获救不回外层事务。
	 */
	fun create(name: String, email: String, password: String): UserRecord =
		try {
			// encode 只在入参为 null 时返回 null
			mapper.insert(name, email, checkNotNull(passwordEncoder.encode(password)))
		} catch (e: DuplicateKeyException) {
			UserErrors.emailTaken(email, e)
		}

	fun get(id: Long): UserRecord =
		mapper.findById(id) ?: UserErrors.notFound(id)

	/** [page] 从 1 开始。 */
	fun search(keyword: String?, page: Int, size: Int): Page<UserRecord> {
		val pattern = keyword?.escapeLike()
		return Page(
			items = mapper.search(pattern, limit = size, offset = (page - 1) * size),
			total = mapper.count(pattern),
		)
	}

	fun rename(id: Long, name: String): UserRecord = tx.write {
		if (mapper.updateName(id, name) == 0) UserErrors.notFound(id)
		mapper.findById(id) ?: UserErrors.notFound(id)
	}

	fun delete(id: Long) {
		if (mapper.deleteById(id) == 0) UserErrors.notFound(id)
	}

	/** 授予角色，已有时不变。用户重新登录后生效：角色写在 token 里，旧 token 到期前仍是原来的角色。 */
	fun grantRole(id: Long, role: Role) {
		tx.write {
			mapper.findById(id) ?: UserErrors.notFound(id)
			mapper.addRole(id, role)
		}
	}

	/** 移除角色，没有该角色时不变。与 [grantRole] 一样，用户重新登录后生效。 */
	fun revokeRole(id: Long, role: Role) {
		tx.write {
			mapper.findById(id) ?: UserErrors.notFound(id)
			mapper.removeRole(id, role)
		}
	}
}
