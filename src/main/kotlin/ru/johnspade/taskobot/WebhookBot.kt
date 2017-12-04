package ru.johnspade.taskobot

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramWebhookBot

@Component
class WebhookBot @Autowired private constructor(
		private val handler: UpdateHandler,
		@Value("\${BOT_TOKEN}") private val token: String,
		@Value("\${BOT_USERNAME}") private val username: String
): TelegramWebhookBot(), BotApiMethodExecutor {

	override fun getBotPath(): String = token

	override fun onWebhookUpdateReceived(update: Update): BotApiMethod<*>? = handler.handle(this, update)

	override fun getBotToken(): String = token

	override fun getBotUsername(): String = username

}
