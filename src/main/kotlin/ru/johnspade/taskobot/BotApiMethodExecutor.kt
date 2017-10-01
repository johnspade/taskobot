package ru.johnspade.taskobot

import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.exceptions.TelegramApiException
import org.telegram.telegrambots.updateshandlers.SentCallback
import java.io.Serializable

interface BotApiMethodExecutor {

	@Throws(TelegramApiException::class)
	fun <T: Serializable, Method: BotApiMethod<T>, Callback: SentCallback<T>> executeAsync(method: Method, callback: Callback)

	@Throws(TelegramApiException::class)
	fun <T: Serializable, Method: BotApiMethod<T>> execute(method: Method): T?

}
