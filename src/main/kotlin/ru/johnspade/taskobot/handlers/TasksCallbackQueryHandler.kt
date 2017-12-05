package ru.johnspade.taskobot.handlers

import org.springframework.beans.factory.annotation.Autowired
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.api.objects.CallbackQuery
import ru.johnspade.taskobot.BotApiMethodExecutor
import ru.johnspade.taskobot.BotController
import ru.johnspade.taskobot.CallbackData
import ru.johnspade.taskobot.CallbackQueryMapping
import ru.johnspade.taskobot.TASKS_CODE
import ru.johnspade.taskobot.service.BotService

@BotController
class TasksCallbackQueryHandler @Autowired constructor(private val botService: BotService) {

	@CallbackQueryMapping(TASKS_CODE)
	fun handle(callbackQuery: CallbackQuery, data: CallbackData, executor: BotApiMethodExecutor): AnswerCallbackQuery {
		if (data.userId == null || data.page == null)
			throw IllegalArgumentException()
		val id1 = data.userId
		val id2 = callbackQuery.from.id
		val page = data.page
		botService.getTasks(id1, id2, page, callbackQuery.message, executor)
		return AnswerCallbackQuery().setCallbackQueryId(callbackQuery.id)
	}

}
