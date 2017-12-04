package ru.johnspade.taskobot

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.telegram.telegrambots.api.methods.AnswerInlineQuery
import org.telegram.telegrambots.api.objects.inlinequery.InlineQuery
import org.telegram.telegrambots.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent
import org.telegram.telegrambots.api.objects.inlinequery.result.InlineQueryResultArticle
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.johnspade.taskobot.service.BotService
import ru.johnspade.taskobot.service.LocaleService
import ru.johnspade.taskobot.service.Messages

@Service
class InlineQueryHandler @Autowired constructor(
		private val messages: Messages,
		private val localeService: LocaleService,
		private val botService: BotService
) {

	fun handle(inlineQuery: InlineQuery): AnswerInlineQuery {
		val user = botService.getOrCreateUser(inlineQuery.from)
		localeService.setLocale(user.language)
		val query = inlineQuery.query
		val messageContent = InputTextMessageContent().enableMarkdown(true).setMessageText("*$query*")
		val button = InlineKeyboardButton().setText(messages.get("tasks.confirm"))
				.setCustomCallbackData(createConfirmTaskCallbackData())
		val markupInline = InlineKeyboardMarkup().setKeyboard(listOf(listOf(button)))
		val article = InlineQueryResultArticle().setInputMessageContent(messageContent).setId("1")
				.setTitle(messages.get("tasks.create")).setDescription(query).setReplyMarkup(markupInline)
		return AnswerInlineQuery().setInlineQueryId(inlineQuery.id).setResults(listOf(article)).setCacheTime(0)
	}

}
