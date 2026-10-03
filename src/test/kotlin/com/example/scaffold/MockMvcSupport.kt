package com.example.scaffold

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.opaqueToken

// @WebMvcTest 测试共用的登录身份：opaqueToken() 直接放入认证结果，不经过 TokenService 校验 token、加载角色

/** 没有角色的普通用户，sub 即 authentication.name。 */
internal fun asUser(id: Long) = opaqueToken().attributes { it["sub"] = id.toString() }

/** 管理员，权限需要直接给出。 */
internal fun asAdmin() = asUser(9).authorities(SimpleGrantedAuthority("ROLE_ADMIN"))
