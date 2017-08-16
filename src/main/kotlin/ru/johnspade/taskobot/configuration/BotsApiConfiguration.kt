package ru.johnspade.taskobot.configuration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.telegram.telegrambots.TelegramBotsApi
import ru.johnspade.taskobot.LongPollingBot
import ru.johnspade.taskobot.WebhookBot
import ru.johnspade.taskobot.dao.User
import ru.johnspade.taskobot.service.UserService
import javax.annotation.PostConstruct

@Configuration
class BotsApiConfiguration @Autowired constructor(
		private val webhookBot: WebhookBot,
		private val longPollingBot: LongPollingBot,
		@Value("\${BOT_IS_WEBHOOK?:}") private val isWebhook: String,
		@Value("\${BOT_EXTERNAL_URL?:}") private val externalUrl: String,
		@Value("\${BOT_INTERNAL_URL?:}") private val internalUrl: String,
		@Value("\${PORT:8080}") private val port: String,
		@Value("\${BOT_TOKEN}") token: String,
		private val userService: UserService
) {

	private val botId = token.split(":")[0].toInt()

	@PostConstruct
	fun start() {
		val botsApi: TelegramBotsApi
		if (isWebhook == "1") {
			botsApi = TelegramBotsApi(externalUrl, "$internalUrl:$port")
			botsApi.registerBot(webhookBot)
		}
		else {
			botsApi = TelegramBotsApi()
			botsApi.registerBot(longPollingBot)
		}
		if (!userService.exists(botId))
			userService.save(User(botId, "Taskobot"))
	}

}
