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
import ru.johnspade.taskobot.createSetLanguage
import ru.johnspade.taskobot.dao.Language
import ru.johnspade.taskobot.service.Messages
import ru.johnspade.taskobot.service.UserService
import ru.johnspade.taskobot.setCustomCallbackData

@BotController
class ChangeLanguageCallbackQueryHandler @Autowired constructor(
        private val userService: UserService,
        private val messages: Messages
) {

    @CallbackQueryMapping(CHANGE_LANGUAGE_CODE)
    fun handle(callbackQuery: CallbackQuery, executor: BotApiMethodExecutor): AnswerCallbackQuery {
        val user = userService.get(callbackQuery.from.id)
        val messageText = messages.get("settings.currentLanguage", arrayOf(user.language.languageName))
        val replyMarkup = InlineKeyboardMarkup()
        replyMarkup.keyboard = Language.values().map { language ->
            listOf(InlineKeyboardButton(language.languageName).setCustomCallbackData(createSetLanguage(language)))
        }
        val editMessageText = EditMessageText().setChatId(callbackQuery.message.chatId)
                .setMessageId(callbackQuery.message.messageId).setText(messageText).setReplyMarkup(replyMarkup)
        executor.executeAsync(editMessageText, EmptyCallback())
        return AnswerCallbackQuery().setCallbackQueryId(callbackQuery.id)
    }

}
