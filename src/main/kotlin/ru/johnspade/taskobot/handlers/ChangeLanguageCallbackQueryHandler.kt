package ru.johnspade.taskobot.handlers

import org.springframework.beans.factory.annotation.Autowired
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.johnspade.taskobot.BotApiMethodExecutor
import ru.johnspade.taskobot.BotController
import ru.johnspade.taskobot.CHANGE_LANGUAGE_CODE
import ru.johnspade.taskobot.CallbackQueryMapping
import ru.johnspade.taskobot.EmptyCallback
import ru.johnspade.taskobot.createChangeLanguageCallbackData
import ru.johnspade.taskobot.dao.Language
import ru.johnspade.taskobot.service.LocaleService
import ru.johnspade.taskobot.service.Messages
import ru.johnspade.taskobot.service.UserService
import ru.johnspade.taskobot.setCustomCallbackData

@BotController
class ChangeLanguageCallbackQueryHandler @Autowired constructor(
		private val userService: UserService,
		private val messages: Messages,
		private val localeService: LocaleService
) {

	@CallbackQueryMapping(CHANGE_LANGUAGE_CODE)
	fun handle(callbackQuery: CallbackQuery, executor: BotApiMethodExecutor): AnswerCallbackQuery {
		var user = userService.get(callbackQuery.from.id)
		user.language = if (user.language == Language.ENGLISH) Language.RUSSIAN else Language.ENGLISH
		user = userService.save(user)
		localeService.setLocale(user.language)
		val messageText = messages.get("settings.currentLanguage", arrayOf(user.language.languageName))
		val answerCallbackQuery = AnswerCallbackQuery().setText(messages.get("settings.languageChanged"))
				.setCallbackQueryId(callbackQuery.id)
		val replyMarkup = InlineKeyboardMarkup()
		replyMarkup.keyboard = listOf(listOf(
				InlineKeyboardButton(messages.get("settings.changeLanguage"))
						.setCustomCallbackData(createChangeLanguageCallbackData())
		))
		val editMessageText = EditMessageText().setChatId(callbackQuery.message.chatId)
				.setMessageId(callbackQuery.message.messageId).setText(messageText).setReplyMarkup(replyMarkup)
		executor.executeAsync(editMessageText, EmptyCallback())
		return answerCallbackQuery
	}

}
