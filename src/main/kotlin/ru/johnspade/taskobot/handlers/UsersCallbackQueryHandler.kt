package ru.johnspade.taskobot.handlers

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.johnspade.taskobot.BotApiMethodExecutor
import ru.johnspade.taskobot.BotController
import ru.johnspade.taskobot.CallbackData
import ru.johnspade.taskobot.CallbackQueryMapping
import ru.johnspade.taskobot.EmptyCallback
import ru.johnspade.taskobot.PAGE_SIZE
import ru.johnspade.taskobot.USERS_CODE
import ru.johnspade.taskobot.createTasksCallbackData
import ru.johnspade.taskobot.createUsersCallbackData
import ru.johnspade.taskobot.service.BotService
import ru.johnspade.taskobot.service.Messages
import ru.johnspade.taskobot.service.UserService
import ru.johnspade.taskobot.setCustomCallbackData

@BotController
class UsersCallbackQueryHandler @Autowired constructor(
		private val userService: UserService,
		private val messages: Messages,
		private val botService: BotService
) {

	@CallbackQueryMapping(USERS_CODE)
	fun handle(callbackQuery: CallbackQuery, data: CallbackData, executor: BotApiMethodExecutor): AnswerCallbackQuery {
		if (data.page == null)
			throw IllegalArgumentException()
		val page = data.page
		val users = userService.getUsersWithTasks(callbackQuery.from.id, PageRequest(page, PAGE_SIZE))

		val replyMarkup = InlineKeyboardMarkup()
		replyMarkup.keyboard = users.content.map {
			listOf(InlineKeyboardButton(botService.getFullUserName(it))
					.setCustomCallbackData(createTasksCallbackData(it.id, 0)))
		}
		if (users.hasPrevious()) {
			val button = InlineKeyboardButton(messages.get("pages.previous"))
					.setCustomCallbackData(createUsersCallbackData(page - 1))
			replyMarkup.keyboard.add(listOf(button))
		}
		if (users.hasNext()) {
			val button = InlineKeyboardButton(messages.get("pages.next"))
					.setCustomCallbackData(createUsersCallbackData(page + 1))
			replyMarkup.keyboard.add(listOf(button))
		}
		val message = callbackQuery.message
		val editMessageText = EditMessageText().setChatId(message.chatId).setMessageId(message.messageId)
				.setText(messages.get("chats.count", arrayOf(users.totalElements))).setReplyMarkup(replyMarkup)
		executor.executeAsync(editMessageText, EmptyCallback())
		return AnswerCallbackQuery().setCallbackQueryId(callbackQuery.id)
	}

}