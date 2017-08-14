package ru.johnspade.taskobot

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.telegram.telegrambots.ApiContextInitializer

@SpringBootApplication
class TaskobotApplication

fun main(args: Array<String>) {
	ApiContextInitializer.init()
    SpringApplication.run(TaskobotApplication::class.java, *args)
}
