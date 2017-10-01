package ru.johnspade.taskobot.service

import org.apache.commons.text.StringEscapeUtils
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
import org.telegram.telegrambots.exceptions.TelegramApiRequestException
import org.telegram.telegrambots.updateshandlers.SentCallback
import ru.johnspade.taskobot.BotApiMethodExecutor
import ru.johnspade.taskobot.CallbackData
import ru.johnspade.taskobot.CallbackDataType
import ru.johnspade.taskobot.dao.Task
import ru.johnspade.taskobot.dao.User
import ru.johnspade.taskobot.getCustomCallbackData
import ru.johnspade.taskobot.setCustomCallbackData
import java.io.Serializable
import java.lang.Exception
import org.telegram.telegrambots.api.objects.User as TelegramUser

private const val PAGE_SIZE = 5

@Service
class UpdateHandler @Autowired constructor(
		private val userService: UserService,
		private val taskService: TaskService,
		@Value("\${BOT_TOKEN}") token: String,
		private val messages: Messages
) {

	private val botId = token.split(":")[0].toInt()

	fun handle(executor: BotApiMethodExecutor, update: Update): BotApiMethod<*>? {
		return with(update) {
			when {
				hasInlineQuery() -> handleInlineQuery(inlineQuery)
				hasChosenInlineQuery() -> handleChosenInlineQuery(chosenInlineQuery)
				hasCallbackQuery() -> handleCallbackQuery(executor, callbackQuery)
				hasMessage() -> handleMessage(message)
				else -> null
			}
		}
	}

	private fun handleInlineQuery(inlineQuery: InlineQuery): AnswerInlineQuery? {
		if (inlineQuery.hasQuery()) {
			val query = inlineQuery.query
			val messageContent = InputTextMessageContent().enableMarkdown(true).setMessageText("*$query*")
			val button = InlineKeyboardButton().setText(messages.get("tasks.confirm"))
					.setCustomCallbackData(CallbackData(CallbackDataType.CONFIRM_TASK))
			val markupInline = InlineKeyboardMarkup().setKeyboard(listOf(listOf(button)))
			val article = InlineQueryResultArticle().setInputMessageContent(messageContent).setId("1")
					.setTitle(messages.get("tasks.create")).setDescription(query).setReplyMarkup(markupInline)
			return AnswerInlineQuery().setInlineQueryId(inlineQuery.id).setResults(listOf(article)).setCacheTime(0)
		}
		return null
	}

	private fun handleChosenInlineQuery(chosenInlineQuery: ChosenInlineQuery): EditMessageReplyMarkup {
		val user = getOrCreateUser(chosenInlineQuery.from)
		val task = taskService.save(Task(user, chosenInlineQuery.query))

		val button = InlineKeyboardButton().setText(messages.get("tasks.confirm"))
				.setCustomCallbackData(CallbackData(CallbackDataType.CONFIRM_TASK, taskId = task.id))
		val markupInline = InlineKeyboardMarkup().setKeyboard(listOf(listOf(button)))
		return EditMessageReplyMarkup().setInlineMessageId(chosenInlineQuery.inlineMessageId)
				.setReplyMarkup(markupInline)
	}

	private fun handleCallbackQuery(executor: BotApiMethodExecutor, callbackQuery: CallbackQuery): AnswerCallbackQuery {
		val data = callbackQuery.getCustomCallbackData()
		return when (data.type) {
			CallbackDataType.CONFIRM_TASK -> {
				if (data.taskId == null)
					throw IllegalArgumentException()
				val task = taskService.get(data.taskId)
				if (task.sender.id == callbackQuery.from.id)
					handleConfirmTaskSenderCallbackQuery(callbackQuery)
				else
					handleConfirmTaskReceiverCallbackQuery(executor, callbackQuery, task)
			}
			CallbackDataType.USERS -> handleUsersCallbackQuery(executor, callbackQuery, data)
			CallbackDataType.TASKS -> handleTasksCallbackQuery(executor, callbackQuery, data)
			CallbackDataType.CHECK_TASK -> handleCheckTaskCallbackQuery(executor, callbackQuery, data)
		}
	}

	private fun handleConfirmTaskSenderCallbackQuery(callbackQuery: CallbackQuery): AnswerCallbackQuery {
		return AnswerCallbackQuery().setText(messages.get("tasks.mustBeConfirmedByReceiver"))
				.setCallbackQueryId(callbackQuery.id)
	}

	private fun handleConfirmTaskReceiverCallbackQuery(executor: BotApiMethodExecutor, callbackQuery: CallbackQuery, task: Task)
			: AnswerCallbackQuery {
		val user = getOrCreateUser(callbackQuery.from)
		task.receiver = user
		taskService.save(task)

		// убираем кнопку подтверждения задачи
		val editMessageReplyMarkup = EditMessageReplyMarkup().setInlineMessageId(callbackQuery.inlineMessageId)
				.setReplyMarkup(InlineKeyboardMarkup())
		executor.executeAsync(editMessageReplyMarkup, EmptyCallback<Serializable>())
		return AnswerCallbackQuery().setCallbackQueryId(callbackQuery.id)
	}

	private fun handleUsersCallbackQuery(executor: BotApiMethodExecutor, callbackQuery: CallbackQuery, data: CallbackData)
			: AnswerCallbackQuery {
		if (data.page == null)
			throw IllegalArgumentException()
		val page = data.page
		val users = userService.getUsersWithTasks(callbackQuery.from.id, PageRequest(page, PAGE_SIZE))

		val replyMarkup = InlineKeyboardMarkup()
		replyMarkup.keyboard = users.content.map {
			listOf(InlineKeyboardButton(getFullUserName(it))
					.setCustomCallbackData(CallbackData(type = CallbackDataType.TASKS, userId = it.id, page = 0)))
		}
		if (users.hasPrevious()) {
			val button = InlineKeyboardButton(messages.get("pages.previous"))
					.setCustomCallbackData(CallbackData(type = CallbackDataType.USERS, page = page - 1))
			replyMarkup.keyboard.add(listOf(button))
		}
		if (users.hasNext()) {
			val button = InlineKeyboardButton(messages.get("pages.next"))
					.setCustomCallbackData(CallbackData(type = CallbackDataType.USERS, page = page + 1))
			replyMarkup.keyboard.add(listOf(button))
		}
		val message = callbackQuery.message
		val editMessageText = EditMessageText().setChatId(message.chatId).setMessageId(message.messageId)
				.setText(messages.get("chats.count", arrayOf(users.totalElements)))
		executor.executeAsync(editMessageText, object : SentCallback<Serializable> {
			override fun onResult(method: BotApiMethod<Serializable>, response: Serializable) {
				executor.executeAsync(EditMessageReplyMarkup().setChatId(message.chatId).setMessageId(message.messageId)
						.setReplyMarkup(replyMarkup), EmptyCallback())
			}
			override fun onException(method: BotApiMethod<Serializable>, e: Exception) {
				throw e
			}
			override fun onError(method: BotApiMethod<Serializable>, e: TelegramApiRequestException) {
				throw e
			}

		})
		return AnswerCallbackQuery().setCallbackQueryId(callbackQuery.id)
	}

	private fun handleTasksCallbackQuery(executor: BotApiMethodExecutor, callbackQuery: CallbackQuery, data: CallbackData)
			: AnswerCallbackQuery {
		if (data.userId == null || data.page == null)
			throw IllegalArgumentException()
		val id1 = data.userId
		val id2 = callbackQuery.from.id
		val page = data.page
		getTasks(executor, id1, id2, page, callbackQuery.message)
		return AnswerCallbackQuery().setCallbackQueryId(callbackQuery.id)
	}

	private fun getTasks(executor: BotApiMethodExecutor, id1: Int, id2: Int, page: Int, message: Message) {
		val user = userService.get(id1)
		val tasks = taskService.getUserTasks(id1, id2, PageRequest(page, PAGE_SIZE))

		val text = StringBuilder("<b>${messages.get("chats.user", arrayOf(getFullUserName(user)))}</b>\n")
		tasks.forEachIndexed { i, (sender, taskText) ->
			text.append("${i + 1}. ${StringEscapeUtils.escapeHtml4(taskText)} <i>– ${sender.firstName}</i>\n")
		}
		text.append("\n<i>${messages.get("tasks.chooseTaskNumber")}</i>")
		val replyMarkup = InlineKeyboardMarkup().setKeyboard(mutableListOf(tasks.mapIndexed { i, task ->
			val callbackData = CallbackData(type = CallbackDataType.CHECK_TASK, taskId = task.id, page = page, userId = id1)
			InlineKeyboardButton("${i + 1}").setCustomCallbackData(callbackData)
		}))
		val keybord = replyMarkup.keyboard
		if (tasks.hasPrevious()) {
			val button = InlineKeyboardButton(messages.get("pages.previous"))
					.setCustomCallbackData(CallbackData(type = CallbackDataType.TASKS, userId = id1, page = page - 1))
			keybord.add(listOf(button))
		}
		if (tasks.hasNext()) {
			val button = InlineKeyboardButton(messages.get("pages.next"))
					.setCustomCallbackData(CallbackData(type = CallbackDataType.TASKS, userId = id1, page = page + 1))
			keybord.add(listOf(button))
		}
		val callbackData = CallbackData(type = CallbackDataType.USERS, page = 0)
		val button = InlineKeyboardButton(messages.get("chats.list")).setCustomCallbackData(callbackData)
		keybord.add(listOf(button))
		executor.executeAsync(EditMessageText().setChatId(message.chatId).setMessageId(message.messageId)
				.enableHtml(true).setText(text.toString()), object : SentCallback<Serializable> {
			override fun onResult(method: BotApiMethod<Serializable>?, response: Serializable?) {
				executor.executeAsync(EditMessageReplyMarkup().setChatId(message.chatId).setMessageId(message.messageId)
						.setReplyMarkup(replyMarkup), EmptyCallback())
			}
			override fun onException(method: BotApiMethod<Serializable>, e: Exception) {
				throw e
			}
			override fun onError(method: BotApiMethod<Serializable>, e: TelegramApiRequestException) {
				throw e
			}
		})
	}

	private fun handleCheckTaskCallbackQuery(executor: BotApiMethodExecutor, callbackQuery: CallbackQuery, data: CallbackData)
			: AnswerCallbackQuery {
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
		getTasks(executor, id1, id2, page, callbackQuery.message)
		val answerCallbackQuery = AnswerCallbackQuery().setText(messages.get("tasks.checked"))
				.setCallbackQueryId(callbackQuery.id)
		val noticeTo = userService.get(id1)
		// Если есть chatId собеседника, отправим ему уведомление о выполнении задачи
		if (noticeTo.chatId != null) {
			val noticeFrom = userService.get(id2)
			val notice = messages.get("tasks.checked.notice", arrayOf(getFullUserName(noticeFrom), task.text))
			executor.executeAsync(SendMessage(noticeTo.chatId, notice), EmptyCallback<Message>())
		}
		return answerCallbackQuery
	}

	private fun getOrCreateUser(telegramUser: TelegramUser, chatId: Long? = null): User {
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

	private fun handleMessage(message: Message): SendMessage? {
		val user = getOrCreateUser(message.from, message.chatId)
		if (message.isCommand) {
			val text = message.text.trimStart()
			if (text.startsWith("/list")) {
				val page = 0
				val users = userService.getUsersWithTasks(message.from.id, PageRequest(page, PAGE_SIZE))
				val replyMarkup = InlineKeyboardMarkup()
				replyMarkup.keyboard = users.content.map {
					listOf(InlineKeyboardButton(getFullUserName(it))
							.setCustomCallbackData(CallbackData(type = CallbackDataType.TASKS, userId = it.id, page = page)))
				}
				if (users.hasNext()) {
					val button = InlineKeyboardButton(messages.get("pages.next"))
							.setCustomCallbackData(CallbackData(type = CallbackDataType.USERS, page = page + 1))
					replyMarkup.keyboard.add(listOf(button))
				}
				return SendMessage(message.chatId, messages.get("chats.count", arrayOf(users.totalElements)))
						.enableMarkdown(true).setReplyMarkup(replyMarkup)
			}
			else if (text.startsWith("/help") || text.startsWith("/start")) {
				val replyMarkup = InlineKeyboardMarkup()
				replyMarkup.keyboard = listOf(listOf(
						InlineKeyboardButton(messages.get("tasks.start")).setSwitchInlineQuery("")
				))
				return SendMessage(message.chatId, messages.get("help")).enableHtml(true)
						.setReplyMarkup(replyMarkup)
			}
			else if (text.startsWith("/create")) {
				return SendMessage(message.chatId, messages.get("tasks.create.personal"))
						.setReplyMarkup(ForceReplyKeyboard())
			}
		}
		else if (message.isReply && message.hasText()) {
			val replyToMessage = message.replyToMessage
			if (replyToMessage.hasText() && replyToMessage.text.startsWith("/create:")) {
				val receiver = userService.get(botId)
				taskService.save(Task(user, message.text, receiver))
				return SendMessage(message.chatId, messages.get("tasks.created", arrayOf(message.text)))
			}
		}
		return null
	}

	private class EmptyCallback<T: Serializable>: SentCallback<T> {
		override fun onException(method: BotApiMethod<T>, e: Exception) {
			throw e
		}
		override fun onError(method: BotApiMethod<T>, e: TelegramApiRequestException) {
			throw e
		}
		override fun onResult(method: BotApiMethod<T>, response: T) {}
	}

	private fun getFullUserName(user: User): String {
		return if (user.id == botId)
			messages.get("tasks.personal")
		else
			"${user.firstName}${if (user.lastName == null) "" else " ${user.lastName}"}"
	}

}
