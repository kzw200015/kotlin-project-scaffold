package com.example.scaffold

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class ScaffoldApplicationTests {

	@Test
	fun 应用上下文能正常启动() {
	}

}
