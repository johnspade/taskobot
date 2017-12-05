package ru.johnspade.taskobot.handlers

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit4.SpringRunner
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.updateshandlers.SentCallback
import ru.johnspade.taskobot.BotApiMethodExecutor
import ru.johnspade.taskobot.CallbackDataType
import ru.johnspade.taskobot.dao.User
import ru.johnspade.taskobot.getCustomCallbackData
import ru.johnspade.taskobot.service.BotService
import ru.johnspade.taskobot.service.LocaleService
import ru.johnspade.taskobot.service.Messages
import ru.johnspade.taskobot.service.TaskRepository
import ru.johnspade.taskobot.service.TaskService
import ru.johnspade.taskobot.service.UserRepository
import ru.johnspade.taskobot.service.UserService
import java.io.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(SpringRunner::class)
@Import(TestConfiguration::class)
@AutoConfigureTestDatabase
@DataJpaTest
abstract class UpdateHandlerTest {

	@Autowired
	protected lateinit var userRepository: UserRepository
	@Autowired
	protected lateinit var taskRepository: TaskRepository
	@Autowired
	protected lateinit var userService: UserService
	@Autowired
	protected lateinit var taskService: TaskService
	@Autowired
	protected lateinit var localeService: LocaleService
	@Autowired
	protected lateinit var botService: BotService
	@Autowired
	protected lateinit var commandHandler: CommandHandler
	@Autowired
	protected lateinit var messages: Messages
	protected lateinit var alice: User
	protected lateinit var bob: User
	protected lateinit var aliceTelegram: org.telegram.telegrambots.api.objects.User
	protected lateinit var bobTelegram: org.telegram.telegrambots.api.objects.User
	protected val emptyExecutor = object: BotApiMethodExecutor {
		override fun <T: Serializable, Method: BotApiMethod<T>, Callback: SentCallback<T>>
				executeAsync(method: Method, callback: Callback) {
		}

		override fun <T: Serializable, Method: BotApiMethod<T>> execute(method: Method): T? = null
	}

	@Before
	fun setup() {
		userService.save(User(0, "Taskobot"))
		alice = userService.save(User(1, "Alice"))
		bob = userService.save(User(2, "Bob", chatId = 100500))
		aliceTelegram = createTelegramUser(alice)
		bobTelegram = createTelegramUser(bob)
	}

	@After
	fun destroy() {
		userRepository.delete(listOf(alice, bob))
	}

	protected fun createTelegramUser(user: User): org.telegram.telegrambots.api.objects.User {
		return mock {
			on { id } doReturn user.id
			on { firstName } doReturn user.firstName
		}
	}

	protected fun createCommand(command: String, user: org.telegram.telegrambots.api.objects.User): Message {
		return mock {
			on { from } doReturn user
			on { hasText() } doReturn true
			on { text } doReturn command
			on { isCommand } doReturn true
		}
	}

	protected fun testHelp(answer: SendMessage, messages: Messages) {
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

	protected fun testSettings(answer: SendMessage, user: User, messages: Messages) {
		assertNotNull(answer)
		answer.validate()
		assertEquals(messages.get("settings.currentLanguage", arrayOf(user.language.languageName)), answer.text)
		val inlineKeybord = answer.replyMarkup as InlineKeyboardMarkup
		assertEquals(1, inlineKeybord.keyboard.size)
		assertEquals(1, inlineKeybord.keyboard[0].size)
		val inlineKeybordButton = inlineKeybord.keyboard[0][0]
		assertEquals(messages.get("settings.changeLanguage"), inlineKeybordButton.text)
		val callbackData = getCustomCallbackData(inlineKeybordButton.callbackData)
		assertEquals(CallbackDataType.CHANGE_LANGUAGE, callbackData.type)
	}

}
