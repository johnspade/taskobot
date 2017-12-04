package ru.johnspade.taskobot.handlers

import org.junit.Test
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.updateshandlers.SentCallback
import ru.johnspade.taskobot.BotApiMethodExecutor
import java.io.Serializable

class StartCommandHandlerTest: UpdateHandlerTest() {

	@Test
	fun handle() {
		val executor = object: BotApiMethodExecutor {
			override fun <T: Serializable, Method: BotApiMethod<T>, Callback: SentCallback<T>>
					executeAsync(method: Method, callback: Callback) {}

			override fun <T: Serializable, Method: BotApiMethod<T>> execute(method: Method): T? {
				when (method) {
					is SendMessage -> testHelp(method, messages)
					else -> throw IllegalStateException()
				}
				return null
			}
		}
		val answer = commandHandler.handleStartCommand(executor, createCommand("/start", aliceTelegram))
		testSettings(answer, alice, messages)
	}

}
