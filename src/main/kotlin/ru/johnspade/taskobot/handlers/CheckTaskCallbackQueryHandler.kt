package ru.johnspade.taskobot.handlers

import org.springframework.beans.factory.annotation.Autowired
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.Message
import ru.johnspade.taskobot.BotApiMethodExecutor
import ru.johnspade.taskobot.BotController
import ru.johnspade.taskobot.CHECK_TASK_CODE
import ru.johnspade.taskobot.CallbackData
import ru.johnspade.taskobot.CallbackQueryMapping
import ru.johnspade.taskobot.EmptyCallback
import ru.johnspade.taskobot.service.BotService
import ru.johnspade.taskobot.service.Messages
import ru.johnspade.taskobot.service.TaskService
import ru.johnspade.taskobot.service.UserService
import java.util.Locale

@BotController
class CheckTaskCallbackQueryHandler @Autowired constructor(
		private val userService: UserService,
		private val taskService: TaskService,
		private val messages: Messages,
		private val botService: BotService
) {

	@CallbackQueryMapping(CHECK_TASK_CODE)
	fun handle(callbackQuery: CallbackQuery, data: CallbackData, executor: BotApiMethodExecutor):
			AnswerCallbackQuery {
		if (data.taskId == null || data.userId == null || data.page == null)
			throw IllegalArgumentException()
		val taskId = data.taskId
		val id1 = data.userId
		val id2 = callbackQuery.from.id
		val page = data.page
		var task = taskService.get(taskId)
		if (!task.done) {
			task.done = true
			task.doneAt = System.currentTimeMillis()
			task = taskService.save(task)
		}
		botService.getTasks(id1, id2, page, callbackQuery.message, executor)
		val answerCallbackQuery = AnswerCallbackQuery().setText(messages.get("tasks.checked"))
				.setCallbackQueryId(callbackQuery.id)
		val noticeTo = userService.get(id1)
		// Если есть chatId собеседника, отправим ему уведомление о выполнении задачи
		if (noticeTo.chatId != null) {
			val noticeFrom = userService.get(id2)
			val notice = messages.get("tasks.checked.notice", arrayOf(botService.getFullUserName(noticeFrom), task.text),
					Locale.forLanguageTag(noticeTo.language.languageTag))
			executor.executeAsync(SendMessage(noticeTo.chatId, notice), EmptyCallback<Message>())
		}
		return answerCallbackQuery
	}

}
