package com.example.scaffold.platform

import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken

/** 当前登录用户的 id（`name` 即用户 id，见 TokenService.introspect）。Controller 参数写 `authentication: Authentication` 即可取得。 */
val Authentication.userId: Long
	get() = name.toLong()

/** 当前请求携带的 token 原文。 */
val Authentication.tokenValue: String
	get() = (this as AbstractOAuth2TokenAuthenticationToken<*>).token.tokenValue
