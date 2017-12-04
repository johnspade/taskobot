package ru.johnspade.taskobot.handlers

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.updateshandlers.SentCallback
import ru.johnspade.taskobot.BotApiMethodExecutor
import ru.johnspade.taskobot.CallbackDataType
import ru.johnspade.taskobot.createChangeLanguageCallbackData
import ru.johnspade.taskobot.dao.Language
import ru.johnspade.taskobot.dao.User
import ru.johnspade.taskobot.getCustomCallbackData
import java.io.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Import(ChangeLanguageCallbackQueryHandler::class)
class ChangeLanguageCallbackQueryHandlerTest: UpdateHandlerTest() {

	@Autowired
	private lateinit var changeLanguageCallbackQueryHandler: ChangeLanguageCallbackQueryHandler

	@Test
	fun handle() {
		val userId = 3
		var user = userService.save(User(userId, "John", language = Language.ENGLISH))
		val userTelegram = createTelegramUser(user)
		val chatId = 1337L
		val messageId = 911
		val callbackQueryId = "112"
		val message = mock<Message> {
			on { getChatId() } doReturn chatId
			on { getMessageId() } doReturn messageId
		}
		val callbackQuery = mock<CallbackQuery> {
			on { from } doReturn userTelegram
			on { data } doReturn createChangeLanguageCallbackData().toString()
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
								messages.get("settings.currentLanguage", arrayOf(user.language.languageName)),
								method.text
						)
						val inlineKeybord = method.replyMarkup as InlineKeyboardMarkup
						assertEquals(1, inlineKeybord.keyboard.size)
						assertEquals(1, inlineKeybord.keyboard[0].size)
						val inlineKeybordButton = inlineKeybord.keyboard[0][0]
						assertEquals(messages.get("settings.changeLanguage"), inlineKeybordButton.text)
						val callbackData = getCustomCallbackData(inlineKeybordButton.callbackData)
						assertEquals(CallbackDataType.CHANGE_LANGUAGE, callbackData.type)
					}
					else -> throw IllegalStateException()
				}
			}
			override fun <T: Serializable, Method: BotApiMethod<T>> execute(method: Method): T? = null
		}
		val answer = changeLanguageCallbackQueryHandler.handle(callbackQuery, executor)
		assertNotNull(answer)
		assertEquals(callbackQueryId, answer.callbackQueryId)
		assertEquals(messages.get("settings.languageChanged"), answer.text)
		user = userService.get(userId)
		assertEquals(Language.RUSSIAN, user.language)
		userRepository.delete(user)
	}

}
