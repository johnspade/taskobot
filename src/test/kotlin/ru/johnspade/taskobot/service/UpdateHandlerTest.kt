package ru.johnspade.taskobot.service

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.junit4.SpringRunner
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.api.methods.AnswerInlineQuery
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.api.objects.inlinequery.ChosenInlineQuery
import org.telegram.telegrambots.api.objects.inlinequery.InlineQuery
import org.telegram.telegrambots.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent
import org.telegram.telegrambots.api.objects.inlinequery.result.InlineQueryResultArticle
import org.telegram.telegrambots.api.objects.replykeyboard.ForceReplyKeyboard
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.updateshandlers.SentCallback
import ru.johnspade.taskobot.BotApiMethodExecutor
import ru.johnspade.taskobot.CallbackData
import ru.johnspade.taskobot.CallbackDataType
import ru.johnspade.taskobot.dao.Task
import ru.johnspade.taskobot.dao.User
import ru.johnspade.taskobot.getCustomCallbackData
import java.io.File
import java.io.Serializable
import java.net.URLClassLoader
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.telegram.telegrambots.api.objects.User as TelegramUser

@RunWith(SpringRunner::class)
@DataJpaTest
class UpdateHandlerTest {

	@Autowired
	private lateinit var userRepository: UserRepository
	@Autowired
	private lateinit var taskRepository: TaskRepository

	private lateinit var userService: UserService
	private lateinit var taskService: TaskService
	private lateinit var updateHandler: UpdateHandler
	private lateinit var messages: Messages
	private lateinit var alice: User
	private lateinit var bob: User
	private lateinit var aliceTelegram: TelegramUser
	private lateinit var bobTelegram: TelegramUser
	private val executor = object : BotApiMethodExecutor {
		override fun <T: Serializable, Method: BotApiMethod<T>, Callback: SentCallback<T>>
				executeAsync(method: Method, callback: Callback) {
		}

		override fun <T: Serializable, Method: BotApiMethod<T>> execute(method: Method): T? = null
	}

	@Before
	fun setup() {
		userService = UserService(userRepository)
		taskService = TaskService(taskRepository)
		val url = File("src/resources/messages").toURI().toURL()
		val messageSource = ResourceBundleMessageSource()
		messageSource.setBundleClassLoader(URLClassLoader(arrayOf(url)))
		messageSource.setBasename("messages/messages")
		messageSource.setDefaultEncoding("UTF-8")
		messages = Messages(messageSource)
		updateHandler = UpdateHandler(
				userService,
				taskService,
				"000000000:00000000000000000000000000000000000",
				messages
		)
		userService.save(User(0, "Taskobot"))
		alice = userService.save(User(1, "Alice"))
		bob = userService.save(User(2, "Bob", chatId = 100500))
		aliceTelegram = createTelegramUser(alice)
		bobTelegram = createTelegramUser(bob)
	}

	private fun createTelegramUser(user: User): TelegramUser {
		return mock {
			on { id } doReturn user.id
			on { firstName } doReturn user.firstName
		}
	}

	private fun createCommand(command: String): Update {
		val message = mock<Message> {
			on { from } doReturn aliceTelegram
			on { hasText() } doReturn true
			on { text } doReturn command
			on { isCommand } doReturn true
		}
		return mock {
			on { hasMessage() } doReturn true
			on { getMessage() } doReturn message
		}
	}

	@Test
	fun answerInlineQuery() {
		val messageText = "new task text"
		val inlineQueryId = "911"
		val inlineQuery = mock<InlineQuery> {
			on { hasQuery() } doReturn true
			on { query } doReturn messageText
			on { id } doReturn inlineQueryId
		}
		val update = mock<Update> {
			on { hasInlineQuery() } doReturn true
			on { getInlineQuery() } doReturn inlineQuery
		}
		val answerInlineQuery = updateHandler.handle(executor, update) as AnswerInlineQuery
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

	@Test
	fun returnChatsListFirstPageOnePage() {
		val task = taskService.save(Task(bob, "task", alice))
		val answer = updateHandler.handle(executor, createCommand("/list")) as SendMessage
		assertNotNull(answer)
		answer.validate()
		assertEquals(messages.get("chats.count", arrayOf(1)), answer.text)
		val inlineKeybord = answer.replyMarkup as InlineKeyboardMarkup
		assertEquals(1, inlineKeybord.keyboard.size)
		assertEquals(1, inlineKeybord.keyboard[0].size)
		val inlineKeybordButton = inlineKeybord.keyboard[0][0]
		assertEquals(bob.firstName, inlineKeybordButton.text)
		val callbackData = getCustomCallbackData(inlineKeybordButton.callbackData)
		assertEquals(CallbackDataType.TASKS, callbackData.type)
		assertEquals(bob.id, callbackData.userId)
		taskRepository.delete(task)
	}

	@Test
	fun returnChatsListFirstPageMultiplePages() {
		val testUsers = mutableListOf<User>()
		val testTasks = mutableListOf<Task>()
		(0..5).forEach {
			val user = userService.save(User(1000 + it + 1, "testUser${it + 1}"))
			testUsers.add(user)
			testTasks.add(taskService.save(Task(user, "task", alice)))
		}
		val answer = updateHandler.handle(executor, createCommand("/list")) as SendMessage
		assertNotNull(answer)
		answer.validate()
		val usersWithTasks = userService.getUsersWithTasks(alice.id, PageRequest(0, 5))
		assertEquals(
				messages.get("chats.count", arrayOf(usersWithTasks.totalElements)),
				answer.text
		)
		val inlineKeyboard = answer.replyMarkup as InlineKeyboardMarkup
		assertEquals(6, inlineKeyboard.keyboard.size)
		(0..4).forEach {
			assertEquals(1, inlineKeyboard.keyboard[it].size)
			val inlineKeybordButton = inlineKeyboard.keyboard[it][0]
			val user = usersWithTasks.content[it]
			assertEquals(user.firstName, inlineKeybordButton.text)
			val callbackData = getCustomCallbackData(inlineKeybordButton.callbackData)
			assertEquals(CallbackDataType.TASKS, callbackData.type)
			assertEquals(user.id, callbackData.userId)
			assertEquals(0, callbackData.page)
		}
		val inlineKeybordButton = inlineKeyboard.keyboard[5][0]
		assertEquals(messages.get("pages.next"), inlineKeybordButton.text)
		val callbackData = getCustomCallbackData(inlineKeybordButton.callbackData)
		assertEquals(CallbackDataType.USERS, callbackData.type)
		assertEquals(1, callbackData.page)
		taskRepository.delete(testTasks)
		userRepository.delete(testUsers)
	}

	@Test
	fun returnChatsPage() {
		val testUsers = mutableListOf<User>()
		val testTasks = mutableListOf<Task>()
		(0..12).forEach {
			val user = userService.save(User(1000 + it + 1, "testUser${it + 1}"))
			testUsers.add(user)
			testTasks.add(taskService.save(Task(user, "task", alice)))
		}
		val chatId = 1337L
		val messageId = 911
		val message = mock<Message> {
			on { getChatId() } doReturn chatId
			on { getMessageId() } doReturn messageId
		}
		val callbackQueryId = "100500"
		val callbackQuery = mock<CallbackQuery> {
			on { from } doReturn aliceTelegram
			on { data } doReturn CallbackData(CallbackDataType.USERS, 1).toString()
			on { getMessage() } doReturn message
			on { id } doReturn callbackQueryId
		}
		val update = mock<Update> {
			on { hasCallbackQuery() } doReturn true
			on { getCallbackQuery() } doReturn callbackQuery
		}
		val usersWithTasks = userService.getUsersWithTasks(alice.id, PageRequest(0, 5))
		val executor = object : BotApiMethodExecutor {
			override fun <T: Serializable, Method: BotApiMethod<T>, Callback: SentCallback<T>>
					executeAsync(method: Method, callback: Callback) {
				when (method) {
					is EditMessageText -> {
						assertEquals(chatId.toString(), method.chatId)
						assertEquals(messageId, method.messageId)
						assertEquals(messages.get("chats.count", arrayOf(usersWithTasks.totalElements)), method.text)
					}
					is EditMessageReplyMarkup -> {
						assertEquals(chatId.toString(), method.chatId)
						assertEquals(messageId, method.messageId)
						val inlineKeyboard = method.replyMarkup.keyboard
						assertEquals(7, inlineKeyboard.size)
						(0..4).forEach {
							assertEquals(1, inlineKeyboard[it].size)
							val inlineKeybordButton = inlineKeyboard[it][0]
							val user = usersWithTasks.content[it]
							assertEquals(user.firstName, inlineKeybordButton.text)
							val callbackData = getCustomCallbackData(inlineKeybordButton.callbackData)
							assertEquals(CallbackDataType.TASKS, callbackData.type)
							assertEquals(user.id, callbackData.userId)
							assertEquals(0, callbackData.page)
						}
						var inlineKeybordButton = inlineKeyboard[5][0]
						assertEquals(messages.get("pages.previous"), inlineKeybordButton.text)
						var callbackData = getCustomCallbackData(inlineKeybordButton.callbackData)
						assertEquals(CallbackDataType.USERS, callbackData.type)
						assertEquals(0, callbackData.page)
						inlineKeybordButton = inlineKeyboard[6][0]
						assertEquals(messages.get("pages.next"), inlineKeybordButton.text)
						callbackData = getCustomCallbackData(inlineKeybordButton.callbackData)
						assertEquals(CallbackDataType.USERS, callbackData.type)
						assertEquals(2, callbackData.page)
					}
					else -> throw IllegalStateException()
				}
			}

			override fun <T: Serializable, Method: BotApiMethod<T>> execute(method: Method): T? = null
		}
		val answer = updateHandler.handle(executor, update) as AnswerCallbackQuery
		answer.validate()
		assertEquals(callbackQueryId, answer.callbackQueryId)
		assertNull(answer.text)
		taskRepository.delete(testTasks)
		userRepository.delete(testUsers)
	}

	@Test
	fun returnHelp() {
		val answer = updateHandler.handle(executor, createCommand("/help")) as SendMessage
		assertNotNull(answer)
		answer.validate()
		assertEquals(messages.get("help"), answer.text)
		assertTrue { answer.toString().contains("parseMode='html'") }
		val inlineKeybord = answer.replyMarkup as InlineKeyboardMarkup
		assertEquals(1, inlineKeybord.keyboard.size)
		assertEquals(1, inlineKeybord.keyboard[0].size)
		val inlineKeybordButton = inlineKeybord.keyboard[0][0]
		assertEquals(messages.get("tasks.start"), inlineKeybordButton.text)
		assertEquals("", inlineKeybordButton.switchInlineQuery)
	}

	@Test
	fun returnCreatePersonalTaskInvitation() {
		val answer = updateHandler.handle(executor, createCommand("/create")) as SendMessage
		assertNotNull(answer)
		answer.validate()
		assertEquals(messages.get("tasks.create.personal"), answer.text)
		assertTrue(answer.replyMarkup is ForceReplyKeyboard)
	}

	@Test
	fun createPersonalTask() {
		val taskText = "new personal task"
		val replyTo = mock<Message> {
			on { hasText() } doReturn true
			on { text } doReturn messages.get("tasks.create.personal")
		}
		val message = mock<Message> {
			on { from } doReturn aliceTelegram
			on { hasText() } doReturn true
			on { text } doReturn taskText
			on { isReply } doReturn true
			on { replyToMessage } doReturn replyTo
		}
		val update = mock<Update> {
			on { hasMessage() } doReturn true
			on { getMessage() } doReturn message
		}
		val answer = updateHandler.handle(executor, update) as SendMessage
		answer.validate()
		assertNotNull(answer)
		val task = taskRepository.findAll().first { it.sender == alice && it.text == taskText }
		assertNotNull(task.createdAt)
		assertEquals(messages.get("tasks.created", arrayOf(task.text)), answer.text)
	}

	@Test
	fun createTask() {
		val taskText = "new task text"
		val inlineMessageId = "911"
		val chosenInlineQuery = mock<ChosenInlineQuery> {
			on { from } doReturn aliceTelegram
			on { query } doReturn taskText
			on { getInlineMessageId() } doReturn inlineMessageId
		}
		val update = mock<Update> {
			on { hasChosenInlineQuery() } doReturn true
			on { getChosenInlineQuery() } doReturn chosenInlineQuery
		}
		val answer = updateHandler.handle(executor, update) as EditMessageReplyMarkup
		answer.validate()
		val task = taskRepository.findAll().first { it.sender == alice && it.text == taskText }
		assertEquals(inlineMessageId, answer.inlineMessageId)
		val inlineKeybord = answer.replyMarkup as InlineKeyboardMarkup
		assertEquals(1, inlineKeybord.keyboard.size)
		assertEquals(1, inlineKeybord.keyboard[0].size)
		val inlineKeybordButton = inlineKeybord.keyboard[0][0]
		assertEquals(messages.get("tasks.confirm"), inlineKeybordButton.text)
		val callbackData = getCustomCallbackData(inlineKeybordButton.callbackData)
		assertEquals(CallbackDataType.CONFIRM_TASK, callbackData.type)
		assertEquals(task.id, callbackData.taskId)
	}

	@Test
	fun confirmTaskSender() {
		val taskText = "new task text"
		val task = taskService.save(Task(alice, taskText))
		val callbackQueryId = "911"
		val callbackQuery = mock<CallbackQuery> {
			on { from } doReturn aliceTelegram
			on { data } doReturn CallbackData(type = CallbackDataType.CONFIRM_TASK, taskId = task.id).toString()
			on { id } doReturn callbackQueryId
		}
		val update = mock<Update> {
			on { hasCallbackQuery() } doReturn true
			on { getCallbackQuery() } doReturn callbackQuery
		}
		val answer = updateHandler.handle(executor, update) as AnswerCallbackQuery
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
		val callbackQuery = mock<CallbackQuery> {
			on { from } doReturn bobTelegram
			on { data } doReturn CallbackData(type = CallbackDataType.CONFIRM_TASK, taskId = task.id).toString()
			on { getInlineMessageId() } doReturn inlineMessageId
			on { id } doReturn callbackQueryId
		}
		val update = mock<Update> {
			on { hasCallbackQuery() } doReturn true
			on { getCallbackQuery() } doReturn callbackQuery
		}
		val executor = object : BotApiMethodExecutor {
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

			override fun <T : Serializable, Method : BotApiMethod<T>> execute(method: Method): T? = null
		}
		val answer = updateHandler.handle(executor, update)  as AnswerCallbackQuery
		answer.validate()
		assertEquals(callbackQueryId, answer.callbackQueryId)
		assertNull(answer.text)

	}

	@Test
	fun returnTasksOnePage() {
		val taskText = "new task text"
		val task = taskService.save(Task(alice, taskText, bob))
		val chatId = 1337L
		val messageId = 911
		val message = mock<Message> {
			on { getChatId() } doReturn chatId
			on { getMessageId() } doReturn messageId
		}
		val callbackQueryId = "100500"
		val callbackQuery = mock<CallbackQuery> {
			on { from } doReturn aliceTelegram
			on { data } doReturn CallbackData(type = CallbackDataType.TASKS, userId = bob.id, page = 0).toString()
			on { getMessage() } doReturn message
			on { id } doReturn callbackQueryId
		}
		val update = mock<Update> {
			on { hasCallbackQuery() } doReturn true
			on { getCallbackQuery() } doReturn callbackQuery
		}
		val executor = object : BotApiMethodExecutor {
			override fun <T: Serializable, Method: BotApiMethod<T>, Callback: SentCallback<T>>
					executeAsync(method: Method, callback: Callback) {
				when (method) {
					is EditMessageText -> {
						assertEquals(chatId.toString(), method.chatId)
						assertEquals(messageId, method.messageId)
						assertEquals(
								"*${messages.get("chats.user", arrayOf(bob.firstName))}*\n1. ${task.text} " +
										"_– ${alice.firstName}_\n\n_${messages.get("tasks.chooseTaskNumber")}_",
								method.text
						)
						assertTrue { method.toString().contains("parseMode=Markdown") }
						callback.onResult(null, null)
					}
					is EditMessageReplyMarkup -> {
						assertEquals(chatId.toString(), method.chatId)
						assertEquals(messageId, method.messageId)
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
		val answer = updateHandler.handle(executor, update) as AnswerCallbackQuery
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
		val callbackQuery = mock<CallbackQuery> {
			on { from } doReturn aliceTelegram
			on { data } doReturn CallbackData(type = CallbackDataType.TASKS, userId = bob.id, page = 1).toString()
			on { getMessage() } doReturn message
			on {id } doReturn callbackQueryId
		}
		val update = mock<Update> {
			on { hasCallbackQuery() } doReturn true
			on { getCallbackQuery() } doReturn callbackQuery
		}
		val tasks = taskService.getUserTasks(bob.id, alice.id, PageRequest(1, 5)).content
		val executor = object : BotApiMethodExecutor {
			override fun <T: Serializable, Method: BotApiMethod<T>, Callback: SentCallback<T>>
					executeAsync(method: Method, callback: Callback) {
				when (method) {
					is EditMessageText -> {
						assertEquals(chatId.toString(), method.chatId)
						assertEquals(messageId, method.messageId)
						val expectedText = StringBuilder("*${messages.get("chats.user", arrayOf(bob.firstName))}*\n")
						(0..4).forEach { expectedText.append("${it + 1}. ${tasks[it].text} _– ${alice.firstName}_\n") }
						expectedText.append("\n_${messages.get("tasks.chooseTaskNumber")}_")
						assertEquals(expectedText.toString(), method.text)
						assertTrue { method.toString().contains("parseMode=Markdown") }
						callback.onResult(null, null)
					}
					is EditMessageReplyMarkup -> {
						assertEquals(chatId.toString(), method.chatId)
						assertEquals(messageId, method.messageId)
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
		val answer = updateHandler.handle(executor, update) as AnswerCallbackQuery
		answer.validate()
		assertEquals(callbackQueryId, answer.callbackQueryId)
		assertNull(answer.text)
		taskRepository.delete(testTasks)
	}

	@Test
	fun checkTaskOnePage() {
		val taskText = "new task text"
		var task = taskService.save(Task(alice, taskText, bob))
		val chatId = 1337L
		val messageId = 911
		val callbackQueryId = "112"
		val message = mock<Message> {
			on { getChatId() } doReturn chatId
			on { getMessageId() } doReturn messageId
		}
		val callbackQuery = mock<CallbackQuery> {
			on { from } doReturn aliceTelegram
			on { data } doReturn
					CallbackData(type = CallbackDataType.CHECK_TASK, userId = bob.id, page = 0, taskId = task.id).toString()
			on { getMessage() } doReturn message
			on { id } doReturn callbackQueryId
		}
		val update = mock<Update> {
			on { hasCallbackQuery() } doReturn true
			on { getCallbackQuery() } doReturn callbackQuery
		}

		val executor = object : BotApiMethodExecutor {
			override fun <T: Serializable, Method: BotApiMethod<T>, Callback: SentCallback<T>>
					executeAsync(method: Method, callback: Callback) {
				when (method) {
					is EditMessageText -> {
						assertEquals(chatId.toString(), method.chatId)
						assertEquals(messageId, method.messageId)
						assertEquals(
								"*${messages.get("chats.user", arrayOf(bob.firstName))}*\n" +
										"\n_${messages.get("tasks.chooseTaskNumber")}_",
								method.text
						)
						assertTrue { method.toString().contains("parseMode=Markdown") }
						callback.onResult(null, null)
					}
					is EditMessageReplyMarkup -> {
						assertEquals(chatId.toString(), method.chatId)
						assertEquals(messageId, method.messageId)
						val inlineKeyboard = method.replyMarkup.keyboard
						assertEquals(2, inlineKeyboard.size)
						val inlineKeybordButton = inlineKeyboard[1][0]
						assertEquals(messages.get("chats.list"), inlineKeybordButton.text)
						val callbackData = getCustomCallbackData(inlineKeybordButton.callbackData)
						assertEquals(CallbackDataType.USERS, callbackData.type)
						assertEquals(0, callbackData.page)
					}
					is SendMessage -> {
						assertEquals(bob.chatId.toString(), method.chatId)
						assertEquals(messages.get("tasks.checked.notice", arrayOf(alice.firstName, taskText)), method.text)
					}
					else -> throw IllegalStateException()
				}
			}

			override fun <T: Serializable, Method: BotApiMethod<T>> execute(method: Method): T? = null
		}
		val answer = updateHandler.handle(executor, update) as AnswerCallbackQuery
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
		val message = mock<Message> {
			on { getChatId() } doReturn chatId
			on { getMessageId() } doReturn messageId
		}
		val callbackQuery = mock<CallbackQuery> {
			on { from } doReturn aliceTelegram
			on { data } doReturn
					CallbackData(type = CallbackDataType.CHECK_TASK, userId = bob.id, page = 1, taskId = task.id).toString()
			on { getMessage() } doReturn message
			on { id } doReturn callbackQueryId
		}
		val update = mock<Update> {
			on { hasCallbackQuery() } doReturn true
			on { getCallbackQuery() } doReturn callbackQuery
		}

		val executor = object : BotApiMethodExecutor {
			override fun <T: Serializable, Method: BotApiMethod<T>, Callback: SentCallback<T>>
					executeAsync(method: Method, callback: Callback) {
				when (method) {
					is EditMessageText -> {
						val tasks = taskService.getUserTasks(bob.id, alice.id, PageRequest(1, 5)).content
						assertEquals(chatId.toString(), method.chatId)
						assertEquals(messageId, method.messageId)
						val expectedText = StringBuilder("*${messages.get("chats.user", arrayOf(bob.firstName))}*\n")
						(0..4).forEach { expectedText.append("${it + 1}. ${tasks[it].text} _– ${alice.firstName}_\n") }
						expectedText.append("\n_${messages.get("tasks.chooseTaskNumber")}_")
						assertEquals(expectedText.toString(), method.text)
						assertTrue { method.toString().contains("parseMode=Markdown") }
						callback.onResult(null, null)
					}
					is EditMessageReplyMarkup -> {
						val tasks = taskService.getUserTasks(bob.id, alice.id, PageRequest(1, 5)).content
						assertEquals(chatId.toString(), method.chatId)
						assertEquals(messageId, method.messageId)
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
					is SendMessage -> {
						assertEquals(bob.chatId.toString(), method.chatId)
						assertEquals(messages.get("tasks.checked.notice", arrayOf(alice.firstName, taskText)), method.text)
					}
					else -> throw IllegalStateException()
				}
			}

			override fun <T: Serializable, Method: BotApiMethod<T>> execute(method: Method): T? = null
		}
		val answer = updateHandler.handle(executor, update) as AnswerCallbackQuery
		assertNotNull(answer)
		task = taskService.get(task.id)
		assertTrue { task.done }
		assertNotNull(task.doneAt)
		assertEquals(callbackQueryId, answer.callbackQueryId)
		assertEquals(messages.get("tasks.checked"), answer.text)
	}

}
