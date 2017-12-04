package ru.johnspade.taskobot.handlers

import org.springframework.beans.factory.annotation.Autowired
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import ru.johnspade.taskobot.BotApiMethodExecutor
import ru.johnspade.taskobot.BotController
import ru.johnspade.taskobot.CONFIRM_TASK_CODE
import ru.johnspade.taskobot.CallbackData
import ru.johnspade.taskobot.CallbackQueryMapping
import ru.johnspade.taskobot.EmptyCallback
import ru.johnspade.taskobot.dao.Task
import ru.johnspade.taskobot.service.BotService
import ru.johnspade.taskobot.service.Messages
import ru.johnspade.taskobot.service.TaskService

@BotController
class ConfirmTaskCallbackQueryHandler @Autowired constructor(
		private val taskService: TaskService,
		private val messages: Messages,
		private val botService: BotService
) {

	@CallbackQueryMapping(CONFIRM_TASK_CODE)
	fun handle(callbackQuery: CallbackQuery, data: CallbackData, executor: BotApiMethodExecutor): AnswerCallbackQuery {
		if (data.taskId == null)
			throw IllegalArgumentException()
		val task = taskService.get(data.taskId)
		return if (task.sender.id == callbackQuery.from.id)
			handleConfirmTaskSenderCallbackQuery(callbackQuery)
		else
			handleConfirmTaskReceiverCallbackQuery(callbackQuery, task, executor)
	}

	private fun handleConfirmTaskSenderCallbackQuery(callbackQuery: CallbackQuery): AnswerCallbackQuery {
		return AnswerCallbackQuery().setText(messages.get("tasks.mustBeConfirmedByReceiver"))
				.setCallbackQueryId(callbackQuery.id)
	}

	private fun handleConfirmTaskReceiverCallbackQuery(callbackQuery: CallbackQuery, task: Task, executor: BotApiMethodExecutor)
			: AnswerCallbackQuery {
		val user = botService.getOrCreateUser(callbackQuery.from)
		task.receiver = user
		taskService.save(task)
		// убираем кнопку подтверждения задачи
		val editMessageReplyMarkup = EditMessageReplyMarkup().setInlineMessageId(callbackQuery.inlineMessageId)
				.setReplyMarkup(InlineKeyboardMarkup())
		executor.executeAsync(editMessageReplyMarkup, EmptyCallback())
		return AnswerCallbackQuery().setCallbackQueryId(callbackQuery.id)
	}

}