package ru.johnspade.taskobot.handlers

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.updateshandlers.SentCallback
import ru.johnspade.taskobot.BotApiMethodExecutor
import ru.johnspade.taskobot.CallbackData
import ru.johnspade.taskobot.CallbackDataType
import ru.johnspade.taskobot.createCheckTaskCallbackData
import ru.johnspade.taskobot.dao.Language
import ru.johnspade.taskobot.dao.Task
import ru.johnspade.taskobot.dao.User
import ru.johnspade.taskobot.getCustomCallbackData
import java.io.Serializable
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.telegram.telegrambots.api.objects.User as TelegramUser

@Import(CheckTaskCallbackQueryHandler::class)
class CheckTaskCallbackQueryHandlerTest: UpdateHandlerTest() {

	@Autowired
	private lateinit var checkTaskCallbackQueryHandler: CheckTaskCallbackQueryHandler

	@Test
	fun checkTaskOnePageSameLanguage() {
		checkTaskOnePage()
	}

	@Test
	fun checkTaskOnePageDifferentLanguages() {
		val user = userService.save(User(3, "John", language = Language.ENGLISH, chatId = 911))
		checkTaskOnePage(user)
		userRepository.delete(user)
	}

	private fun checkTaskOnePage(user: User = bob) {
		val taskText = "new task text"
		var task = taskService.save(Task(alice, taskText, user))
		val chatId = 1337L
		val messageId = 911
		val callbackQueryId = "112"
		val data = createCheckTaskCallbackData(task.id, 0, user.id)
		val message = mock<Message> {
			on { getChatId() } doReturn chatId
			on { getMessageId() } doReturn messageId
		}
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
								"<b>${messages.get("chats.user", arrayOf(user.firstName))}</b>\n" +
										"\n<i>${messages.get("tasks.chooseTaskNumber")}</i>",
								method.text
						)
						assertTrue { method.toString().contains("parseMode=html") }
						val inlineKeyboard = method.replyMarkup.keyboard
						assertEquals(2, inlineKeyboard.size)
						val inlineKeybordButton = inlineKeyboard[1][0]
						assertEquals(messages.get("chats.list"), inlineKeybordButton.text)
						val callbackData = getCustomCallbackData(inlineKeybordButton.callbackData) as CallbackData
						assertEquals(CallbackDataType.USERS, callbackData.type)
						assertEquals(0, callbackData.page)
					}
					is SendMessage -> {
						assertEquals(user.chatId.toString(), method.chatId)
						assertEquals(
								messages.get("tasks.checked.notice", arrayOf(alice.firstName, taskText),
										Locale.forLanguageTag(user.language.languageTag)),
								method.text
						)
					}
					else -> throw IllegalStateException()
				}
			}

			override fun <T: Serializable, Method: BotApiMethod<T>> execute(method: Method): T? = null
		}
		val answer = checkTaskCallbackQueryHandler.handle(callbackQuery, data, executor)
		assertNotNull(answer)
		task = taskService.get(task.id)
		assertTrue { task.done }
		assertNotNull(task.doneAt)
		assertEquals(callbackQueryId, answer.callbackQueryId)
		assertEquals(messages.get("tasks.checked"), answer.text)
	}

	@Test
	fun checkTaskMultiplePages() {
		val taskText = "new task text"
		val testTasks = mutableListOf<Task>()
		(0..12).forEach { testTasks.add(taskService.save(Task(alice, taskText, bob))) }
		var task = testTasks[8]
		val chatId = 1337L
		val messageId = 911
		val callbackQueryId = "112"
		val data = createCheckTaskCallbackData(task.id, 1, bob.id)
		val message = mock<Message> {
			on { getChatId() } doReturn chatId
			on { getMessageId() } doReturn messageId
		}
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
						val tasks = taskService.getUserTasks(bob.id, alice.id, PageRequest(1, 5)).content
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
							val callbackData = getCustomCallbackData(inlineKeybordButton.callbackData) as CallbackData
							assertEquals(CallbackDataType.CHECK_TASK, callbackData.type)
							assertEquals(tasks[it].id, callbackData.taskId)
							assertEquals(1, callbackData.page)
							assertEquals(bob.id, callbackData.userId)
						}
						var inlineKeybordButton = inlineKeyboard[1][0]
						assertEquals(messages.get("pages.previous"), inlineKeybordButton.text)
						var callbackData = getCustomCallbackData(inlineKeybordButton.callbackData) as CallbackData
						assertEquals(CallbackDataType.TASKS, callbackData.type)
						assertEquals(0, callbackData.page)
						assertEquals(bob.id, callbackData.userId)
						inlineKeybordButton = inlineKeyboard[2][0]
						assertEquals(messages.get("pages.next"), inlineKeybordButton.text)
						callbackData = getCustomCallbackData(inlineKeybordButton.callbackData) as CallbackData
						assertEquals(CallbackDataType.TASKS, callbackData.type)
						assertEquals(2, callbackData.page)
						assertEquals(bob.id, callbackData.userId)
						inlineKeybordButton = inlineKeyboard[3][0]
						assertEquals(messages.get("chats.list"), inlineKeybordButton.text)
						callbackData = getCustomCallbackData(inlineKeybordButton.callbackData) as CallbackData
						assertEquals(CallbackDataType.USERS, callbackData.type)
						assertEquals(0, callbackData.page)
					}
					is SendMessage -> {
						assertEquals(bob.chatId.toString(), method.chatId)
						assertEquals(
								messages.get("tasks.checked.notice", arrayOf(alice.firstName, taskText),
										Locale.forLanguageTag(bob.language.languageTag)),
								method.text
						)
					}
					else -> throw IllegalStateException()
				}
			}

			override fun <T: Serializable, Method: BotApiMethod<T>> execute(method: Method): T? = null
		}
		val answer = checkTaskCallbackQueryHandler.handle(callbackQuery, data, executor)
		assertNotNull(answer)
		task = taskService.get(task.id)
		assertTrue { task.done }
		assertNotNull(task.doneAt)
		assertEquals(callbackQueryId, answer.callbackQueryId)
		assertEquals(messages.get("tasks.checked"), answer.text)
	}

}
