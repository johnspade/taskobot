package ru.johnspade.taskobot.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
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
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.bots.DefaultAbsSender
import org.telegram.telegrambots.exceptions.TelegramApiRequestException
import org.telegram.telegrambots.updateshandlers.SentCallback
import ru.johnspade.taskobot.CallbackData
import ru.johnspade.taskobot.CallbackDataType
import ru.johnspade.taskobot.dao.Task
import ru.johnspade.taskobot.dao.User
import java.io.Serializable
import java.lang.Exception
import java.util.*
import org.telegram.telegrambots.api.objects.User as TelegramUser

@Service
class UpdateHandler @Autowired private constructor(
		private val userService: UserService,
		private val taskService: TaskService,
		private val objectMapper: ObjectMapper,
		@Value("\${BOT_TOKEN}") token: String,
		private val messages: Messages
) {

	private val botId = token.split(":")[0].toInt()

	companion object {
		private val PAGE_SIZE = 5
	}

	fun handleUpdate(bot: DefaultAbsSender, update: Update): Optional<out BotApiMethod<*>> {
		return with(update) {
			when {
				hasInlineQuery() -> handleInlineQuery(inlineQuery)
				hasChosenInlineQuery() -> handleChosenInlineQuery(chosenInlineQuery)
				hasCallbackQuery() -> handleCallbackQuery(bot, callbackQuery)
				hasMessage() -> handleMessage(message)
				else -> Optional.empty()
			}
		}
	}

	fun handleInlineQuery(inlineQuery: InlineQuery): Optional<AnswerInlineQuery> {
		if (inlineQuery.hasQuery()) {
			val query = inlineQuery.query
			val messageContent = InputTextMessageContent().enableMarkdown(true).setMessageText("*$query*")
			val button = InlineKeyboardButton().setText(messages.get("tasks.confirm"))
					.setJsonCallbackData(CallbackData(CallbackDataType.CONFIRM_TASK))
			val markupInline = InlineKeyboardMarkup().setKeyboard(listOf(listOf(button)))
			val article = InlineQueryResultArticle().setInputMessageContent(messageContent).setId("1")
					.setTitle(messages.get("tasks.create")).setDescription(query).setReplyMarkup(markupInline)
			return Optional.of(AnswerInlineQuery().setInlineQueryId(inlineQuery.id).setResults(listOf(article)))
		}
		return Optional.empty()
	}

	fun handleChosenInlineQuery(chosenInlineQuery: ChosenInlineQuery): Optional<EditMessageReplyMarkup> {
		val user = getOrCreateUser(chosenInlineQuery.from)
		val task = taskService.save(Task(user, chosenInlineQuery.query, System.currentTimeMillis()))

		val button = InlineKeyboardButton().setText(messages.get("tasks.confirm"))
				.setJsonCallbackData(CallbackData(CallbackDataType.CONFIRM_TASK, taskId = task.id))
		val markupInline = InlineKeyboardMarkup().setKeyboard(listOf(listOf(button)))
		return Optional.of(EditMessageReplyMarkup().setInlineMessageId(chosenInlineQuery.inlineMessageId)
				.setReplyMarkup(markupInline))
	}

	fun handleCallbackQuery(bot: DefaultAbsSender, callbackQuery: CallbackQuery): Optional<BotApiMethod<*>> {
		val data = callbackQuery.getJsonCallbackData()
		return when (data.type) {
			CallbackDataType.CONFIRM_TASK -> {
				if (data.taskId == null)
					throw IllegalArgumentException()
				val task = taskService.get(data.taskId)
				if (task.sender.id == callbackQuery.from.id)
					handleConfirmTaskSenderCallbackQuery(callbackQuery)
				else
					handleConfirmTaskReceiverCallbackQuery(callbackQuery, task)
			}
			CallbackDataType.USERS -> handleUsersCallbackQuery(bot, callbackQuery, data)
			CallbackDataType.TASKS -> handleTasksCallbackQuery(bot, callbackQuery, data)
			CallbackDataType.CHECK_TASK -> handleCheckTaskCallbackQuery(bot, callbackQuery, data)
		}
	}

	fun handleConfirmTaskSenderCallbackQuery(callbackQuery: CallbackQuery): Optional<BotApiMethod<*>> {
		return Optional.of(AnswerCallbackQuery().setText(messages.get("tasks.mustBeConfirmedByReceiver"))
				.setCallbackQueryId(callbackQuery.id))
	}

	fun handleConfirmTaskReceiverCallbackQuery(callbackQuery: CallbackQuery, task: Task): Optional<BotApiMethod<*>> {
		val user = getOrCreateUser(callbackQuery.from)
		task.receiver = user
		taskService.save(task)

		// убираем кнопку подтверждения задачи
		return Optional.of(EditMessageReplyMarkup().setInlineMessageId(callbackQuery.inlineMessageId)
				.setReplyMarkup(InlineKeyboardMarkup()))
	}

	fun handleUsersCallbackQuery(bot: DefaultAbsSender, callbackQuery: CallbackQuery, data: CallbackData)
			: Optional<BotApiMethod<*>> {
		if (data.page == null)
			throw IllegalArgumentException()
		val page = data.page
		val users = userService.getUsersWithTasks(callbackQuery.from.id, PageRequest(page, PAGE_SIZE))

		val replyMarkup = InlineKeyboardMarkup()
		replyMarkup.keyboard = users.content.map {
			listOf(InlineKeyboardButton(getFullUserName(it))
					.setJsonCallbackData(CallbackData(type = CallbackDataType.TASKS, userId = it.id, page = 0)))
		}
		if (users.hasPrevious()) {
			val button = InlineKeyboardButton(messages.get("pages.previous"))
					.setJsonCallbackData(CallbackData(type = CallbackDataType.USERS, page = page - 1))
			replyMarkup.keyboard.add(listOf(button))
		}
		if (users.hasNext()) {
			val button = InlineKeyboardButton(messages.get("pages.next"))
					.setJsonCallbackData(CallbackData(type = CallbackDataType.USERS, page = page + 1))
			replyMarkup.keyboard.add(listOf(button))
		}
		val message = callbackQuery.message
		val editMessageText = EditMessageText().setChatId(message.chatId).setMessageId(message.messageId)
				.setText(messages.get("chats.count", arrayOf(users.count()))).enableMarkdown(true)
		bot.executeAsync(editMessageText, object : SentCallback<Serializable> {
			override fun onResult(method: BotApiMethod<Serializable>, response: Serializable) {
				bot.executeAsync(EditMessageReplyMarkup().setChatId(message.chatId).setMessageId(message.messageId)
						.setReplyMarkup(replyMarkup), EmptyCallback())
			}

			override fun onException(method: BotApiMethod<Serializable>, exception: Exception) {}
			override fun onError(method: BotApiMethod<Serializable>, apiException: TelegramApiRequestException) {}

		})
		return Optional.empty()
	}

	fun handleTasksCallbackQuery(bot: DefaultAbsSender, callbackQuery: CallbackQuery, data: CallbackData)
			: Optional<BotApiMethod<*>> {
		if (data.userId == null || data.page == null)
			throw IllegalArgumentException()
		val id1 = data.userId
		val id2 = callbackQuery.from.id
		val page = data.page
		return getTasks(bot, id1, id2, page, callbackQuery.message)
	}

	fun getTasks(bot: DefaultAbsSender, id1: Int, id2: Int, page: Int, message: Message)
			: Optional<BotApiMethod<*>> {
		val user = userService.get(id1)
		val tasks = taskService.getUserTasks(id1, id2, PageRequest(page, PAGE_SIZE))

		val text = StringBuilder("*Чат: ${getFullUserName(user)}*\n")
		tasks.forEachIndexed { i, (sender, taskText) -> text.append("${i + 1}. $taskText _– ${sender.firstName}_\n") }
		text.append("\n_${messages.get("tasks.chooseTaskNumber")}_")
		val replyMarkup = InlineKeyboardMarkup().setKeyboard(mutableListOf(tasks.mapIndexed { i, task ->
			val callbackData = CallbackData(type = CallbackDataType.CHECK_TASK, taskId = task.id, page = page, userId = id1)
			InlineKeyboardButton("${i + 1}").setJsonCallbackData(callbackData)
		}))
		val keybord = replyMarkup.keyboard
		if (tasks.hasPrevious()) {
			val button = InlineKeyboardButton(messages.get("pages.previous"))
					.setJsonCallbackData(CallbackData(type = CallbackDataType.TASKS, userId = id1, page = page - 1))
			keybord.add(listOf(button))
		}
		if (tasks.hasNext()) {
			val button = InlineKeyboardButton(messages.get("pages.next"))
					.setJsonCallbackData(CallbackData(type = CallbackDataType.TASKS, userId = id1, page = page + 1))
			keybord.add(listOf(button))
		}
		val callbackData = CallbackData(type = CallbackDataType.USERS, page = 0)
		val button = InlineKeyboardButton(messages.get("chats.list")).setJsonCallbackData(callbackData)
		keybord.add(listOf(button))
		bot.executeAsync(EditMessageText().setChatId(message.chatId).setMessageId(message.messageId)
				.enableMarkdown(true).setText(text.toString()), object : SentCallback<Serializable> {
			override fun onResult(method: BotApiMethod<Serializable>, response: Serializable) {
				bot.executeAsync(EditMessageReplyMarkup().setChatId(message.chatId).setMessageId(message.messageId)
						.setReplyMarkup(replyMarkup), EmptyCallback())
			}
			override fun onException(method: BotApiMethod<Serializable>, exception: Exception) {}
			override fun onError(method: BotApiMethod<Serializable>, apiException: TelegramApiRequestException) {}
		})
		return Optional.empty()
	}

	fun handleCheckTaskCallbackQuery(bot: DefaultAbsSender, callbackQuery: CallbackQuery, data: CallbackData)
			: Optional<BotApiMethod<*>> {
		if (data.taskId == null || data.userId == null || data.page == null)
			throw IllegalArgumentException()
		val taskId = data.taskId
		val id1 = data.userId
		val id2 = callbackQuery.from.id
		val page = data.page
		var task = taskService.get(taskId)
		if (!task.done) {
			task.done = true
			task.doneAt = System.currentTimeMillis()
			task = taskService.save(task)
		}
		getTasks(bot, id1, id2, page, callbackQuery.message)
		val answerCallbackQuery = AnswerCallbackQuery().setText(messages.get("tasks.checked", arrayOf(task.text)))
				.setCallbackQueryId(callbackQuery.id)
		val noticeTo = userService.get(id1)
		val noticeFrom = userService.get(id2)
		return if (noticeTo.chatId == null)
			Optional.of(answerCallbackQuery)
		else {
			// Если есть chatId собеседника, отправим ему уведомление о выполнении задачи
			val notice = messages.get("tasks.checked.notice", arrayOf(getFullUserName(noticeFrom), task.text))
			bot.executeAsync(SendMessage(noticeTo.chatId, notice), EmptyCallback<Message>())
			Optional.of(answerCallbackQuery)
		}
	}

	fun getOrCreateUser(telegramUser: TelegramUser, chatId: Long? = null): User {
		var user: User
		if (userService.exists(telegramUser.id)) {
			user = userService.get(telegramUser.id)
			val update = user.firstName != telegramUser.firstName ||
					user.lastName != telegramUser.lastName ||
					user.username != telegramUser.userName ||
					user.languageCode != telegramUser.languageCode ||
					(chatId != null && chatId != user.chatId)
			if (update) {
				user.firstName = telegramUser.firstName
				user.lastName = telegramUser.lastName
				user.username = telegramUser.userName
				user.languageCode = telegramUser.languageCode
				if (chatId != null)
					user.chatId = chatId
				user = userService.save(user)
			}
		}
		else {
			user = userService.save(User(telegramUser.id, telegramUser.firstName, telegramUser.lastName,
					telegramUser.userName, telegramUser.languageCode, chatId))
		}
		return user
	}

	fun handleMessage(message: Message): Optional<SendMessage> {
		val user = getOrCreateUser(message.from, message.chatId)
		if (message.isCommand) {
			val text = message.text.trimStart()
			if (text.startsWith("/list")) {
				val page = 0
				val users = userService.getUsersWithTasks(message.from.id, PageRequest(page, PAGE_SIZE))
				val replyMarkup = InlineKeyboardMarkup()
				replyMarkup.keyboard = users.content.map {
					listOf(InlineKeyboardButton(getFullUserName(it))
							.setJsonCallbackData(CallbackData(type = CallbackDataType.TASKS, userId = it.id, page = page)))
				}
				if (users.hasNext()) {
					val button = InlineKeyboardButton(messages.get("pages.next"))
							.setJsonCallbackData(CallbackData(type = CallbackDataType.USERS, page = page + 1))
					replyMarkup.keyboard.add(listOf(button))
				}
				val sendMessage = SendMessage(message.chatId, messages.get("chats.count", arrayOf(users.count())))
						.enableMarkdown(true).setReplyMarkup(replyMarkup)
				return Optional.of(sendMessage)
			}
			else if (text.startsWith("/help") || text.startsWith("/start")) {
				val replyMarkup = InlineKeyboardMarkup()
				replyMarkup.keyboard = listOf(listOf(
						InlineKeyboardButton(messages.get("tasks.start")).setSwitchInlineQuery("")
				))
				val sendMessage = SendMessage(message.chatId, messages.get("help")).enableHtml(true)
						.setReplyMarkup(replyMarkup)
				return Optional.of(sendMessage)
			}
			else if (text.startsWith("/create")) {
				return Optional.of(SendMessage(message.chatId, messages.get("tasks.create.personal"))
						.setReplyMarkup(ForceReplyKeyboard()))
			}
		}
		else if (message.isReply && message.hasText()) {
			val replyToMessage = message.replyToMessage
			if (replyToMessage.hasText() && replyToMessage.text.startsWith("/create:")) {
				val receiver = userService.get(botId)
				taskService.save(Task(user, message.text, System.currentTimeMillis(), receiver))
				return Optional.of(SendMessage(message.chatId, messages.get("tasks.created", arrayOf(message.text))))
			}
		}
		return Optional.empty()
	}

	fun InlineKeyboardButton.setJsonCallbackData(callbackData: CallbackData): InlineKeyboardButton {
		setCallbackData(objectMapper.writeValueAsString(callbackData))
		return this
	}

	fun CallbackQuery.getJsonCallbackData(): CallbackData {
		return objectMapper.readValue(data, CallbackData::class.java)
	}

	class EmptyCallback<T: Serializable>: SentCallback<T> {
		override fun onException(method: BotApiMethod<T>, exception: Exception) {}
		override fun onError(method: BotApiMethod<T>, apiException: TelegramApiRequestException) {}
		override fun onResult(method: BotApiMethod<T>, response: T) {}
	}

	fun getFullUserName(user: User): String {
		return if (user.id == botId)
			messages.get("tasks.personal")
		else
			"${user.firstName}${if (user.lastName == null) "" else " ${user.lastName}"}"
	}

}
