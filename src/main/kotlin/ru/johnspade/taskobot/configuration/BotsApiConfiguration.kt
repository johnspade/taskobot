package ru.johnspade.taskobot.configuration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.telegram.telegrambots.TelegramBotsApi
import ru.johnspade.taskobot.Taskobot
import javax.annotation.PostConstruct

@Configuration
class BotsApiConfiguration @Autowired constructor(
		private val taskobot: Taskobot,
		@Value("\${BOT_EXTERNAL_URL}") private val externalUrl: String,
		@Value("\${BOT_INTERNAL_URL}") private val internalUrl: String,
		@Value("\${PORT}") private val port: String
) {

	@PostConstruct
	fun start() {
		val botsApi = TelegramBotsApi(externalUrl, "$internalUrl:$port")
		botsApi.registerBot(taskobot)
	}

}
