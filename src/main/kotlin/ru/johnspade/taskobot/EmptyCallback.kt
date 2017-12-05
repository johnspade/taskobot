package ru.johnspade.taskobot

import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.exceptions.TelegramApiRequestException
import org.telegram.telegrambots.updateshandlers.SentCallback
import java.io.Serializable
import java.lang.Exception

class EmptyCallback<T: Serializable>: SentCallback<T> {

	override fun onException(method: BotApiMethod<T>, e: Exception) {
		throw e
	}

	override fun onError(method: BotApiMethod<T>, e: TelegramApiRequestException) {
		throw e
	}

	override fun onResult(method: BotApiMethod<T>, response: T) {}

}
