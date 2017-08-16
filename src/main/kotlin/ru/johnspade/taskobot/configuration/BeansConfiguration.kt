package ru.johnspade.taskobot.configuration

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.net.URI

@Configuration
class BeansConfiguration {

	@Bean
	fun dataSource(): DriverManagerDataSource {
		val dbUri = URI(System.getenv("DATABASE_URL"))
		val dataSource = DriverManagerDataSource()
		dataSource.setDriverClassName("org.postgresql.Driver")
		dataSource.url = "jdbc:postgresql://" + dbUri.host + ':' + dbUri.port + dbUri.path
		val userInfo = dbUri.userInfo.split(":")
		dataSource.username = userInfo[0]
		dataSource.password = userInfo[1]
		return dataSource
	}

	@Bean
	fun objectMapper(): ObjectMapper = ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
			.registerModule(KotlinModule())

}
