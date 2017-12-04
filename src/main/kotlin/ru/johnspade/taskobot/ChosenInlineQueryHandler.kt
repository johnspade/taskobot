package ru.johnspade.taskobot

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.api.objects.inlinequery.ChosenInlineQuery
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.johnspade.taskobot.dao.Task
import ru.johnspade.taskobot.service.BotService
import ru.johnspade.taskobot.service.LocaleService
import ru.johnspade.taskobot.service.Messages
import ru.johnspade.taskobot.service.TaskService

@Service
class ChosenInlineQueryHandler @Autowired constructor(
		private val taskService: TaskService,
		private val messages: Messages,
		private val localeService: LocaleService,
		private val botService: BotService
) {

	fun handle(chosenInlineQuery: ChosenInlineQuery): EditMessageReplyMarkup {
		val user = botService.getOrCreateUser(chosenInlineQuery.from)
		localeService.setLocale(user.language)
		val task = taskService.save(Task(user, chosenInlineQuery.query))

		val button = InlineKeyboardButton().setText(messages.get("tasks.confirm"))
				.setCustomCallbackData(createConfirmTaskCallbackData(task.id))
		val markupInline = InlineKeyboardMarkup().setKeyboard(listOf(listOf(button)))
		return EditMessageReplyMarkup().setInlineMessageId(chosenInlineQuery.inlineMessageId)
				.setReplyMarkup(markupInline)
	}

}
