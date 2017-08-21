package ru.johnspade.taskobot

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramWebhookBot
import ru.johnspade.taskobot.service.UpdateHandler

@Component
class WebhookBot @Autowired private constructor(
		private val handler: UpdateHandler,
		@Value("\${BOT_TOKEN}") private val token: String,
		@Value("\${BOT_USERNAME}") private val username: String
) : TelegramWebhookBot(), BotApiMethodExecutor {

	override fun getBotPath(): String {
		return token
	}

	override fun onWebhookUpdateReceived(update: Update): BotApiMethod<*>? {
		return handler.handle(this, update)
	}

	override fun getBotToken(): String {
		return token
	}

	override fun getBotUsername(): String {
		return username
	}

}
