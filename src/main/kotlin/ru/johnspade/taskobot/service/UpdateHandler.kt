package ru.johnspade.taskobot.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.api.methods.AnswerInlineQuery
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.api.objects.inlinequery.ChosenInlineQuery
import org.telegram.telegrambots.api.objects.inlinequery.InlineQuery
import org.telegram.telegrambots.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent
import org.telegram.telegrambots.api.objects.inlinequery.result.InlineQueryResultArticle
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.johnspade.taskobot.CallbackData
import ru.johnspade.taskobot.CallbackDataType
import ru.johnspade.taskobot.dao.Task
import ru.johnspade.taskobot.dao.User
import java.util.*
import org.telegram.telegrambots.api.objects.User as TelegramUser

@Service
class UpdateHandler @Autowired private constructor(
		private val userService: UserService,
		private val taskService: TaskService,
		private val objectMapper: ObjectMapper
) {

	companion object {
		private val PAGE_SIZE = 5
	}

	fun handleInlineQuery(inlineQuery: InlineQuery): Optional<BotApiMethod<*>> {
		if (inlineQuery.hasQuery()) {
			val query = inlineQuery.query
			val messageContent = InputTextMessageContent().enableMarkdown(true).setMessageText("*$query*")
			val button = InlineKeyboardButton().setText("Подтвердить задачу")
					.setJsonCallbackData(CallbackData(CallbackDataType.CONFIRM_TASK))
			val markupInline = InlineKeyboardMarkup().setKeyboard(listOf(listOf(button)))
			val article = InlineQueryResultArticle().setInputMessageContent(messageContent).setId("1")
					.setTitle("Создать задачу").setDescription(query).setReplyMarkup(markupInline)
			val answerInlineQuery = AnswerInlineQuery().setInlineQueryId(inlineQuery.id).setResults(listOf(article))
			return Optional.of(answerInlineQuery)
		}
		return Optional.empty()
	}

	fun handleChosenInlineQuery(chosenInlineQuery: ChosenInlineQuery): Optional<BotApiMethod<*>> {
		val user = getOrCreateUser(chosenInlineQuery.from)
		val task = Task(chosenInlineQuery.inlineMessageId, user, null, chosenInlineQuery.query, false,
				System.currentTimeMillis(), null)
		taskService.save(task)
		return Optional.empty()
	}

	fun handleCallbackQuery(callbackQuery: CallbackQuery): Optional<BotApiMethod<*>> {
		val data = callbackQuery.getJsonCallbackData()
		when (data.type) {
			CallbackDataType.CONFIRM_TASK -> {
				val task = taskService.get(callbackQuery.inlineMessageId)
				if (task.sender.id == callbackQuery.from.id)
					return handleConfirmTaskSenderCallbackQuery(callbackQuery)
				else
					return handleConfirmTaskReceiverCallbackQuery(callbackQuery, task)
			}
			CallbackDataType.USERS -> return handleUsersCallbackQuery(callbackQuery, data)
			CallbackDataType.TASKS -> return handleTasksCallbackQuery(callbackQuery, data)
			CallbackDataType.CHECK_TASK -> return handleCheckTaskCallbackQuery(callbackQuery, data)
		}
	}

	fun handleConfirmTaskSenderCallbackQuery(callbackQuery: CallbackQuery): Optional<BotApiMethod<*>> {
		return Optional.of(AnswerCallbackQuery().setText("Задача должна быть подтверждена собеседником")
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

	fun handleUsersCallbackQuery(callbackQuery: CallbackQuery, data: CallbackData): Optional<BotApiMethod<*>> {
		val page = data.page!!
		val users = userService.getUsersWithTasks(callbackQuery.from.id, PageRequest(page, PAGE_SIZE))
		val replyMarkup = InlineKeyboardMarkup()
		replyMarkup.keyboard = users.content.map {
			listOf(InlineKeyboardButton("${it.firstName} ${it.lastName?:""}")
					.setJsonCallbackData(CallbackData(type = CallbackDataType.TASKS, uid = it.id, page = 0)))
		}
		if (users.hasPrevious()) {
			val button = InlineKeyboardButton("Предыдущая страница")
					.setJsonCallbackData(CallbackData(type = CallbackDataType.USERS, page = page - 1))
			replyMarkup.keyboard.add(listOf(button))
		}
		if (users.hasNext()) {
			val button = InlineKeyboardButton("Следующая страница")
					.setJsonCallbackData(CallbackData(type = CallbackDataType.USERS, page = page + 1))
			replyMarkup.keyboard.add(listOf(button))
		}
		val message = callbackQuery.message
		return Optional.of(EditMessageReplyMarkup().setMessageId(message.messageId)
				.setChatId(message.chatId).setReplyMarkup(replyMarkup))
	}

	fun handleTasksCallbackQuery(callbackQuery: CallbackQuery, data: CallbackData): Optional<BotApiMethod<*>> {
		val id1 = data.uid!!
		val id2 = callbackQuery.from.id
		val page = data.page!!
		val user = userService.get(id1)
		val tasks = taskService.getUserTasks(id1, id2, PageRequest(page, PAGE_SIZE))
		val text = StringBuilder("*Чат: ${user.firstName} ${user.lastName?:""}*\n")
		tasks.forEachIndexed { i, task -> text.append("${i + 1}. ${task.text}\n") }
		text.append("\n_Выберите номер задачи, чтобы отметить ее выполненной._")
		val replyMarkup = InlineKeyboardMarkup().setKeyboard(mutableListOf(tasks.mapIndexed { i, (id) ->
			InlineKeyboardButton("${i + 1}")
					.setJsonCallbackData(CallbackData(type = CallbackDataType.CHECK_TASK, tid = id))
		}))
		if (tasks.hasNext()) {
			val button = InlineKeyboardButton("Следующая страница")
					.setJsonCallbackData(CallbackData(type = CallbackDataType.TASKS, uid = id1, page = page + 1))
			replyMarkup.keyboard.add(listOf(button))
		}
		return Optional.of(SendMessage(callbackQuery.message.chatId, text.toString()).enableMarkdown(true)
				.setReplyMarkup(replyMarkup))
	}

	fun handleCheckTaskCallbackQuery(callbackQuery: CallbackQuery, data: CallbackData): Optional<BotApiMethod<*>> {
		val taskId = data.tid!!
		var task = taskService.get(taskId)
		task.done = true
		task.doneAt = System.currentTimeMillis()
		task = taskService.save(task)
		return Optional.of(SendMessage(callbackQuery.message.chatId, "Задача \"${task.text}\" отмечена как выполненная."))
	}

	fun getOrCreateUser(telegramUser: TelegramUser): User {
		var user: User
		if (userService.exists(telegramUser.id)) {
			user = userService.get(telegramUser.id)
			user.firstName = telegramUser.firstName
			user.lastName = telegramUser.lastName
			user.username = telegramUser.userName
			user.languageCode = telegramUser.languageCode
			user = userService.save(user)
		}
		else {
			user = userService.save(User(telegramUser.id, telegramUser.firstName, telegramUser.lastName,
					telegramUser.userName, telegramUser.languageCode))
		}
		return user
	}

	fun handleMessage(message: Message): Optional<BotApiMethod<*>> {
		if (message.isCommand) {
			val text = message.text.trimStart()
			if (text.startsWith("/list")) {
				val page = 0
				val users = userService.getUsersWithTasks(message.from.id, PageRequest(page, PAGE_SIZE))
				val replyMarkup = InlineKeyboardMarkup()
				replyMarkup.keyboard = users.content.map {
					listOf(InlineKeyboardButton("${it.firstName} ${it.lastName?:""}")
							.setJsonCallbackData(CallbackData(type = CallbackDataType.TASKS, uid = it.id, page = page)))
				}
				if (users.hasNext()) {
					val button = InlineKeyboardButton("Следующая страница")
							.setJsonCallbackData(CallbackData(type = CallbackDataType.USERS, page = page + 1))
					replyMarkup.keyboard.add(listOf(button))
				}
				val sendMessage = SendMessage(message.chatId, "Чатов с задачами: ${users.count()}").enableMarkdown(true)
						.setReplyMarkup(replyMarkup)
				return Optional.of(sendMessage)
			}
			else if (text.startsWith("/help") || text.startsWith("/start")) {
				val replyMarkup = InlineKeyboardMarkup()
				replyMarkup.keyboard = listOf(listOf(
						InlineKeyboardButton("Начать создавать задачи").setSwitchInlineQuery("")
				))
				val help = """
					|Таскобот позволяет создавать совместные задачи. В чате с собеседником напиши
					| <code>@tasko_bot {задача}</code> и нажми <b>Создать задачу</b>. После подтверждения
					| собеседником будет создана общая задача. Для получения списка своих задач напиши мне команду /list.
				""".trimMargin().replace("\n", "")
				val sendMessage = SendMessage(message.chatId, help).enableHtml(true).setReplyMarkup(replyMarkup)
				return Optional.of(sendMessage)
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

}
