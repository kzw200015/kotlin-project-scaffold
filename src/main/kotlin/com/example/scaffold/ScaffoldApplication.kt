package com.example.scaffold

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ScaffoldApplication

fun main(args: Array<String>) {
	runApplication<ScaffoldApplication>(*args)
}
