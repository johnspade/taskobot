package ru.johnspade.taskobot.handlers

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.support.ResourceBundleMessageSource
import ru.johnspade.taskobot.service.BotService
import ru.johnspade.taskobot.service.LocaleService
import ru.johnspade.taskobot.service.Messages
import ru.johnspade.taskobot.service.TaskService
import ru.johnspade.taskobot.service.UserService
import java.io.File
import java.net.URLClassLoader

@TestConfiguration
@Import(LocaleService::class, UserService::class, TaskService::class, BotService::class, CommandHandler::class)
class TestConfiguration {

	@Bean
	fun messages(): Messages {
		val url = File("src/resources/messages").toURI().toURL()
		val messageSource = ResourceBundleMessageSource()
		messageSource.setBundleClassLoader(URLClassLoader(arrayOf(url)))
		messageSource.setBasename("messages/messages")
		messageSource.setDefaultEncoding("UTF-8")
		messageSource.setFallbackToSystemLocale(false)
		return Messages(messageSource)
	}

}
