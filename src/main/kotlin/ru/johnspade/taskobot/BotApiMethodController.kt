package ru.johnspade.taskobot

import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.api.objects.Update
import java.lang.reflect.Method

class BotApiMethodController(private val bean: Any, private val method: Method) {

	fun process(executor: BotApiMethodExecutor, update: Update): BotApiMethod<*>? {
		val invokeParams = mutableListOf<Any?>()
		method.parameters.forEach {
			invokeParams.add(when (it.type) {
				Update::class.java -> update
				CallbackQuery::class.java -> update.callbackQuery
				CallbackData::class.java -> getCustomCallbackData(update.callbackQuery.data)
				BotApiMethodExecutor::class.java -> executor
				Message::class.java -> update.message
				else -> null
			})
		}
		return method.invoke(bean, *invokeParams.toTypedArray()) as BotApiMethod<*>?
	}

}