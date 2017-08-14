package ru.johnspade.taskobot

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramWebhookBot
import ru.johnspade.taskobot.service.UpdateHandler
import java.util.*

@Component
class Taskobot @Autowired private constructor(
		private val handler: UpdateHandler,
		@Value("\${BOT_TOKEN}") private val token: String,
		@Value("\${BOT_USERNAME}") private val username: String
) : TelegramWebhookBot() {

	override fun getBotPath(): String {
		return token
	}

	override fun onWebhookUpdateReceived(update: Update): BotApiMethod<*>? {
		val result: Optional<BotApiMethod<*>> = with(update) {
			when {
				hasInlineQuery() -> handler.handleInlineQuery(inlineQuery)
				hasChosenInlineQuery() -> handler.handleChosenInlineQuery(chosenInlineQuery)
				hasCallbackQuery() -> handler.handleCallbackQuery(callbackQuery)
				hasMessage() -> handler.handleMessage(message)
				else -> Optional.empty()
			}
		}
		return result.orElse(null)
	}

	override fun getBotToken(): String {
		return token
	}

	override fun getBotUsername(): String {
		return username
	}

}
