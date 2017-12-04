package ru.johnspade.taskobot.handlers

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.updateshandlers.SentCallback
import ru.johnspade.taskobot.BotApiMethodExecutor
import ru.johnspade.taskobot.CallbackDataType
import ru.johnspade.taskobot.createTasksCallbackData
import ru.johnspade.taskobot.dao.Task
import ru.johnspade.taskobot.getCustomCallbackData
import java.io.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(TasksCallbackQueryHandler::class)
class TasksCallbackQueryHandlerTest: UpdateHandlerTest() {

	@Autowired
	private lateinit var tasksCallbackQueryHandler: TasksCallbackQueryHandler

	@Test
	fun returnTasksOnePage() {
		val taskText = "new task text"
		returnOneTask(taskText)
	}

	@Test
	fun escapeHtmlInTaskText() {
		returnOneTask("<b>new task text</b>", "&lt;b&gt;new task text&lt;/b&gt;")
	}

	private fun returnOneTask(taskText: String, returnedTaskText: String = taskText) {
		val task = taskService.save(Task(alice, taskText, bob))
		val chatId = 1337L
		val messageId = 911
		val message = mock<Message> {
			on { getChatId() } doReturn chatId
			on { getMessageId() } doReturn messageId
		}
		val callbackQueryId = "100500"
		val data = createTasksCallbackData(bob.id, 0)
		val callbackQuery = mock<CallbackQuery> {
			on { from } doReturn aliceTelegram
			on { it.data } doReturn data.toString()
			on { getMessage() } doReturn message
			on { id } doReturn callbackQueryId
		}
		val executor = object: BotApiMethodExecutor {
			override fun <T: Serializable, Method: BotApiMethod<T>, Callback: SentCallback<T>>
					executeAsync(method: Method, callback: Callback) {
				when (method) {
					is EditMessageText -> {
						assertEquals(chatId.toString(), method.chatId)
						assertEquals(messageId, method.messageId)
						assertEquals(
								"<b>${messages.get("chats.user", arrayOf(bob.firstName))}</b>\n1. $returnedTaskText " +
										"<i>– ${alice.firstName}</i>\n\n<i>${messages.get("tasks.chooseTaskNumber")}</i>",
								method.text
						)
						assertTrue { method.toString().contains("parseMode=html") }
						val inlineKeyboard = method.replyMarkup.keyboard
						assertEquals(2, inlineKeyboard.size)
						var inlineKeybordButton = inlineKeyboard[0][0]
						assertEquals("1", inlineKeybordButton.text)
						var callbackData = getCustomCallbackData(inlineKeybordButton.callbackData)
						assertEquals(CallbackDataType.CHECK_TASK, callbackData.type)
						assertEquals(task.id, callbackData.taskId)
						assertEquals(0, callbackData.page)
						assertEquals(bob.id, callbackData.userId)
						inlineKeybordButton = inlineKeyboard[1][0]
						assertEquals(messages.get("chats.list"), inlineKeybordButton.text)
						callbackData = getCustomCallbackData(inlineKeybordButton.callbackData)
						assertEquals(CallbackDataType.USERS, callbackData.type)
						assertEquals(0, callbackData.page)
					}
					else -> throw IllegalStateException()
				}
			}

			override fun <T: Serializable, Method: BotApiMethod<T>> execute(method: Method): T? = null
		}
		val answer = tasksCallbackQueryHandler.handle(callbackQuery, data, executor)
		answer.validate()
		assertEquals(callbackQueryId, answer.callbackQueryId)
		assertNull(answer.text)
		taskRepository.delete(task)
	}

	@Test
	fun returnTasksMultiplePages() {
		val testTasks = mutableListOf<Task>()
		(0..12).forEach { testTasks.add(taskService.save(Task(alice, "task", bob))) }
		val chatId = 1337L
		val messageId = 911
		val message = mock<Message> {
			on { getChatId() } doReturn chatId
			on { getMessageId() } doReturn messageId
		}
		val callbackQueryId = "100500"
		val data = createTasksCallbackData(bob.id, 1)
		val callbackQuery = mock<CallbackQuery> {
			on { from } doReturn aliceTelegram
			on { it.data } doReturn data.toString()
			on { getMessage() } doReturn message
			on {id } doReturn callbackQueryId
		}
		val tasks = taskService.getUserTasks(bob.id, alice.id, PageRequest(1, 5)).content
		val executor = object: BotApiMethodExecutor {
			override fun <T: Serializable, Method: BotApiMethod<T>, Callback: SentCallback<T>>
					executeAsync(method: Method, callback: Callback) {
				when (method) {
					is EditMessageText -> {
						assertEquals(chatId.toString(), method.chatId)
						assertEquals(messageId, method.messageId)
						val expectedText = StringBuilder("<b>${messages.get("chats.user", arrayOf(bob.firstName))}</b>\n")
						(0..4).forEach { expectedText.append("${it + 1}. ${tasks[it].text} <i>– ${alice.firstName}</i>\n") }
						expectedText.append("\n<i>${messages.get("tasks.chooseTaskNumber")}</i>")
						assertEquals(expectedText.toString(), method.text)
						assertTrue { method.toString().contains("parseMode=html") }
						val inlineKeyboard = method.replyMarkup.keyboard
						assertEquals(4, inlineKeyboard.size)
						assertEquals(5, inlineKeyboard[0].size)
						(0..4).forEach {
							val inlineKeybordButton = inlineKeyboard[0][it]
							assertEquals("${it + 1}", inlineKeybordButton.text)
							val callbackData = getCustomCallbackData(inlineKeybordButton.callbackData)
							assertEquals(CallbackDataType.CHECK_TASK, callbackData.type)
							assertEquals(tasks[it].id, callbackData.taskId)
							assertEquals(1, callbackData.page)
							assertEquals(bob.id, callbackData.userId)
						}
						var inlineKeybordButton = inlineKeyboard[1][0]
						assertEquals(messages.get("pages.previous"), inlineKeybordButton.text)
						var callbackData = getCustomCallbackData(inlineKeybordButton.callbackData)
						assertEquals(CallbackDataType.TASKS, callbackData.type)
						assertEquals(0, callbackData.page)
						assertEquals(bob.id, callbackData.userId)
						inlineKeybordButton = inlineKeyboard[2][0]
						assertEquals(messages.get("pages.next"), inlineKeybordButton.text)
						callbackData = getCustomCallbackData(inlineKeybordButton.callbackData)
						assertEquals(CallbackDataType.TASKS, callbackData.type)
						assertEquals(2, callbackData.page)
						assertEquals(bob.id, callbackData.userId)
						inlineKeybordButton = inlineKeyboard[3][0]
						assertEquals(messages.get("chats.list"), inlineKeybordButton.text)
						callbackData = getCustomCallbackData(inlineKeybordButton.callbackData)
						assertEquals(CallbackDataType.USERS, callbackData.type)
						assertEquals(0, callbackData.page)
					}
					else -> throw IllegalStateException()
				}
			}

			override fun <T: Serializable, Method: BotApiMethod<T>> execute(method: Method): T? = null
		}
		val answer = tasksCallbackQueryHandler.handle(callbackQuery, data, executor)
		answer.validate()
		assertEquals(callbackQueryId, answer.callbackQueryId)
		assertNull(answer.text)
		taskRepository.delete(testTasks)
	}

}
