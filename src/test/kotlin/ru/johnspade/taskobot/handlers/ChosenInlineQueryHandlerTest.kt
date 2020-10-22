package ru.johnspade.taskobot.handlers

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.telegram.telegrambots.api.objects.inlinequery.ChosenInlineQuery
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import ru.johnspade.taskobot.CallbackData
import ru.johnspade.taskobot.CallbackDataType
import ru.johnspade.taskobot.ChosenInlineQueryHandler
import ru.johnspade.taskobot.getCustomCallbackData
import kotlin.test.assertEquals

@Import(ChosenInlineQueryHandler::class)
class ChosenInlineQueryHandlerTest: UpdateHandlerTest() {

	@Autowired
	private lateinit var chosenInlineQueryHandler: ChosenInlineQueryHandler

	@Test
	fun createShortTask() {
		createTask()
	}

	@Test
	fun createBigTask() {
		val stringBuilder = StringBuilder()
		(0..4095).forEach { stringBuilder.append("a") }
		createTask(stringBuilder.toString())
	}

	private fun createTask(taskText: String = "new task text") {
		val inlineMessageId = "911"
		val chosenInlineQuery = mock<ChosenInlineQuery> {
			on { from } doReturn aliceTelegram
			on { query } doReturn taskText
			on { getInlineMessageId() } doReturn inlineMessageId
		}
		val answer = chosenInlineQueryHandler.handle(chosenInlineQuery)
		answer.validate()
		val task = taskRepository.findAll().first { it.sender == alice && it.text == taskText }
		assertEquals(inlineMessageId, answer.inlineMessageId)
		val inlineKeybord = answer.replyMarkup as InlineKeyboardMarkup
		assertEquals(1, inlineKeybord.keyboard.size)
		assertEquals(1, inlineKeybord.keyboard[0].size)
		val inlineKeybordButton = inlineKeybord.keyboard[0][0]
		assertEquals(messages.get("tasks.confirm"), inlineKeybordButton.text)
		val callbackData = getCustomCallbackData(inlineKeybordButton.callbackData) as CallbackData
		assertEquals(CallbackDataType.CONFIRM_TASK, callbackData.type)
		assertEquals(task.id, callbackData.taskId)
	}

}
