package ru.johnspade.taskobot.handlers

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.telegram.telegrambots.api.objects.inlinequery.InlineQuery
import org.telegram.telegrambots.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent
import org.telegram.telegrambots.api.objects.inlinequery.result.InlineQueryResultArticle
import ru.johnspade.taskobot.CallbackDataType
import ru.johnspade.taskobot.InlineQueryHandler
import ru.johnspade.taskobot.getCustomCallbackData
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Import(InlineQueryHandler::class)
class InlineQueryHandlerTest: UpdateHandlerTest() {

	@Autowired
	private lateinit var inlineQueryHandler: InlineQueryHandler

	@Test
	fun handle() {
		val messageText = "new task text"
		val inlineQueryId = "911"
		val inlineQuery = mock<InlineQuery> {
			on { hasQuery() } doReturn true
			on { from } doReturn aliceTelegram
			on { query } doReturn messageText
			on { id } doReturn inlineQueryId
		}
		val answerInlineQuery = inlineQueryHandler.handle(inlineQuery)
		assertNotNull(answerInlineQuery)
		answerInlineQuery.validate()
		assertEquals(inlineQueryId, answerInlineQuery.inlineQueryId)
		assertEquals(1, answerInlineQuery.results.size)
		val result = answerInlineQuery.results[0] as InlineQueryResultArticle
		result.validate()
		assertEquals(messageText, result.description)
		assertEquals(messages.get("tasks.create"), result.title)
		assertEquals(1, result.replyMarkup.keyboard.size)
		assertEquals(1, result.replyMarkup.keyboard[0].size)
		val inlineKeyboardButton = result.replyMarkup.keyboard[0][0]
		assertEquals(messages.get("tasks.confirm"), inlineKeyboardButton.text)
		assertEquals(CallbackDataType.CONFIRM_TASK, getCustomCallbackData(inlineKeyboardButton.callbackData).type)
		val inputTextMessageContent = result.inputMessageContent as InputTextMessageContent
		assertEquals("*$messageText*", inputTextMessageContent.messageText)
		assertEquals("Markdown", inputTextMessageContent.parseMode)
	}

}
