package ru.johnspade.taskobot.handlers

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.updateshandlers.SentCallback
import ru.johnspade.taskobot.BotApiMethodExecutor
import ru.johnspade.taskobot.createConfirmTaskCallbackData
import ru.johnspade.taskobot.dao.Task
import java.io.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Import(ConfirmTaskCallbackQueryHandler::class)
class ConfirmTaskCallbackQueryHandlerTest: UpdateHandlerTest() {

	@Autowired
	private lateinit var confirmTaskCallbackQueryHandler: ConfirmTaskCallbackQueryHandler

	@Test
	fun confirmTaskSender() {
		val taskText = "new task text"
		val task = taskService.save(Task(alice, taskText))
		val callbackQueryId = "911"
		val data = createConfirmTaskCallbackData(task.id)
		val callbackQuery = mock<CallbackQuery> {
			on { from } doReturn aliceTelegram
			on { it.data } doReturn data.toString()
			on { id } doReturn callbackQueryId
		}
		val answer = confirmTaskCallbackQueryHandler.handle(callbackQuery, data, emptyExecutor)
		answer.validate()
		assertEquals(callbackQueryId, answer.callbackQueryId)
		assertEquals(messages.get("tasks.mustBeConfirmedByReceiver"), answer.text)
	}

	@Test
	fun confirmTaskReceiver() {
		val taskText = "new task text"
		val inlineMessageId = "911"
		var task = taskService.save(Task(alice, taskText))
		val callbackQueryId = "100500"
		val data = createConfirmTaskCallbackData(task.id)
		val callbackQuery = mock<CallbackQuery> {
			on { from } doReturn bobTelegram
			on { it.data } doReturn data.toString()
			on { getInlineMessageId() } doReturn inlineMessageId
			on { id } doReturn callbackQueryId
		}
		val executor = object: BotApiMethodExecutor {
			override fun <T: Serializable, Method: BotApiMethod<T>, Callback: SentCallback<T>>
					executeAsync(method: Method, callback: Callback) {
				when (method) {
					is EditMessageReplyMarkup -> {
						task = taskService.get(task.id)
						assertEquals(bob, task.receiver)
						assertEquals(inlineMessageId, method.inlineMessageId)
						val replyMarkup = method.replyMarkup as InlineKeyboardMarkup
						assertEquals(0, replyMarkup.keyboard.size)
					}
					else -> throw IllegalStateException()
				}
			}

			override fun <T: Serializable, Method: BotApiMethod<T>> execute(method: Method): T? = null
		}
		val answer = confirmTaskCallbackQueryHandler.handle(callbackQuery, data, executor)
		answer.validate()
		assertEquals(callbackQueryId, answer.callbackQueryId)
		assertNull(answer.text)
	}

}
