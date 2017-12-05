package ru.johnspade.taskobot

import org.springframework.stereotype.Component

@Component
class BotControllerContainer {

	private val callbackQueryControllers: MutableMap<Int, BotApiMethodController> = mutableMapOf()
	private val messageControllers: MutableMap<String, BotApiMethodController> = mutableMapOf()

	fun addCallbackQueryController(code: Int, controller: BotApiMethodController) {
		if (callbackQueryControllers.containsKey(code))
			throw IllegalArgumentException("Callback query controller for code $code already added")
		callbackQueryControllers.put(code, controller)
	}

	fun getCallbackQueryController(code: Int): BotApiMethodController? {
		return callbackQueryControllers[code]
	}

	fun addMessageController(command: String, controller: BotApiMethodController) {
		if (messageControllers.containsKey(command))
			throw IllegalArgumentException("Message controller for command $command already added")
		 messageControllers.put(command, controller)
	}

	fun getMessageController(message: String): BotApiMethodController? {
		return messageControllers[message]
	}

}
