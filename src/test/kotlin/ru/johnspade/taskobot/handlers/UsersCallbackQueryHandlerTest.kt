package ru.johnspade.taskobot.handlers

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.updateshandlers.SentCallback
import ru.johnspade.taskobot.BotApiMethodExecutor
import ru.johnspade.taskobot.CallbackDataType
import ru.johnspade.taskobot.createUsersCallbackData
import ru.johnspade.taskobot.dao.Task
import ru.johnspade.taskobot.dao.User
import ru.johnspade.taskobot.getCustomCallbackData
import java.io.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Import(UsersCallbackQueryHandler::class)
class UsersCallbackQueryHandlerTest: UpdateHandlerTest() {

	@Autowired
	private lateinit var usersCallbackQueryHandler: UsersCallbackQueryHandler

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
		val data = createUsersCallbackData(1)
		val callbackQuery = mock<CallbackQuery> {
			on { from } doReturn aliceTelegram
			on { it.data } doReturn data.toString()
			on { getMessage() } doReturn message
			on { id } doReturn callbackQueryId
		}
		val usersWithTasks = userService.getUsersWithTasks(alice.id, PageRequest(0, 5))
		val executor = object: BotApiMethodExecutor {
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
		val answer = usersCallbackQueryHandler.handle(callbackQuery, data, executor)
		answer.validate()
		assertEquals(callbackQueryId, answer.callbackQueryId)
		assertNull(answer.text)
		taskRepository.delete(testTasks)
		userRepository.delete(testUsers)
	}

}
