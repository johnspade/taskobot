package ru.johnspade.taskobot

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import ru.johnspade.taskobot.service.UpdateHandler
import java.io.Serializable

@Component
class LongPollingBot @Autowired private constructor(
		private val handler: UpdateHandler,
		@Value("\${BOT_TOKEN}") private val token: String,
		@Value("\${BOT_USERNAME}") private val username: String
) : TelegramLongPollingBot(), BotApiMethodExecutor {

	override fun getBotToken(): String {
		return token
	}

	override fun getBotUsername(): String {
		return username
	}

	override fun onUpdateReceived(update: Update) {
		handler.handle(this, update)?.let {
			@Suppress("UNCHECKED_CAST")
			execute(it as BotApiMethod<Serializable>)
		}
	}

}
