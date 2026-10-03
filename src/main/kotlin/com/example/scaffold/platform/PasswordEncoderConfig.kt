package com.example.scaffold.platform

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder

/** 单独放一个配置类，方便 `@MybatisTest` 等切片测试只导入它。 */
@Configuration
class PasswordEncoderConfig {
	/** 默认 BCrypt。哈希带算法前缀（如 `{bcrypt}$2a$10$...`），以后换算法时旧哈希仍能校验。 */
	@Bean
	fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
}
