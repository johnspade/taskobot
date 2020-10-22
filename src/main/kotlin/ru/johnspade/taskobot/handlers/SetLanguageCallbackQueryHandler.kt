package ru.johnspade.taskobot.handlers

import org.springframework.beans.factory.annotation.Autowired
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.api.objects.CallbackQuery
import ru.johnspade.taskobot.BotApiMethodExecutor
import ru.johnspade.taskobot.BotController
import ru.johnspade.taskobot.CallbackQueryMapping
import ru.johnspade.taskobot.EmptyCallback
import ru.johnspade.taskobot.SET_LANGUAGE_CODE
import ru.johnspade.taskobot.SetLanguage
import ru.johnspade.taskobot.service.LocaleService
import ru.johnspade.taskobot.service.Messages
import ru.johnspade.taskobot.service.UserService

@BotController
class SetLanguageCallbackQueryHandler @Autowired constructor(
        private val userService: UserService,
        private val messages: Messages,
        private val localeService: LocaleService
) {
    @CallbackQueryMapping(SET_LANGUAGE_CODE)
    fun handle(callbackQuery: CallbackQuery, data: SetLanguage, executor: BotApiMethodExecutor): AnswerCallbackQuery {
        var user = userService.get(callbackQuery.from.id)
        user.language = data.language
        user = userService.save(user)
        localeService.setLocale(user.language)
        val messageText = messages.get("settings.currentLanguage", arrayOf(user.language.languageName))
        val editMessageText = EditMessageText().setChatId(callbackQuery.message.chatId)
                .setMessageId(callbackQuery.message.messageId).setText(messageText)
        executor.executeAsync(editMessageText, EmptyCallback())
        return AnswerCallbackQuery().setText(messages.get("settings.languageChanged")).setCallbackQueryId(callbackQuery.id)
    }
}
