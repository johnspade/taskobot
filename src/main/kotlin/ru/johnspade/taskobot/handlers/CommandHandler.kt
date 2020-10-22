package ru.johnspade.taskobot.handlers

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.api.objects.replykeyboard.ForceReplyKeyboard
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.johnspade.taskobot.BotApiMethodExecutor
import ru.johnspade.taskobot.BotController
import ru.johnspade.taskobot.MessageMapping
import ru.johnspade.taskobot.PAGE_SIZE
import ru.johnspade.taskobot.createChangeLanguageCallbackData
import ru.johnspade.taskobot.createTasksCallbackData
import ru.johnspade.taskobot.createUsersCallbackData
import ru.johnspade.taskobot.dao.Task
import ru.johnspade.taskobot.dao.User
import ru.johnspade.taskobot.service.BotService
import ru.johnspade.taskobot.service.Messages
import ru.johnspade.taskobot.service.TaskService
import ru.johnspade.taskobot.service.UserService
import ru.johnspade.taskobot.setCustomCallbackData

@BotController
class CommandHandler @Autowired constructor(
		private val userService: UserService,
		private val messages: Messages,
		private val botService: BotService,
		private val taskService: TaskService,
		@Value("\${BOT_TOKEN}") token: String
) {

	private val botId = token.split(":")[0].toInt()

	@MessageMapping("list")
	fun handleListCommand(executor: BotApiMethodExecutor, message: Message): SendMessage {
		val page = 0
		val users = userService.getUsersWithTasks(message.from.id, PageRequest(page, PAGE_SIZE))
		val replyMarkup = InlineKeyboardMarkup()
		replyMarkup.keyboard = users.content.map {
			listOf(InlineKeyboardButton(botService.getFullUserName(it))
					.setCustomCallbackData(createTasksCallbackData(it.id, page)))
		}
		if (users.hasNext()) {
			val button = InlineKeyboardButton(messages.get("pages.next"))
					.setCustomCallbackData(createUsersCallbackData(page + 1))
			replyMarkup.keyboard.add(listOf(button))
		}
		return SendMessage(message.chatId, messages.get("chats.count", arrayOf(users.totalElements)))
				.enableMarkdown(true).setReplyMarkup(replyMarkup)
	}

	@MessageMapping("help")
	fun handleHelpCommand(message: Message): SendMessage {
		return createHelpMessage(message.chatId)
	}

	@MessageMapping("start")
	fun handleStartCommand(executor: BotApiMethodExecutor, message: Message): SendMessage {
		val user = botService.getOrCreateUser(message.from, message.chatId)
		executor.execute(createHelpMessage(message.chatId))
		return createSettingsMessage(message.chatId, user)
	}

	@MessageMapping("create")
	fun handleCreateCommand(message: Message): SendMessage {
		val text = message.text
		val task = text.substring(text.indexOf("/create") + 7, text.length).trim()
		return if (task.isNotBlank()) {
			val receiver = userService.get(botId)
			val user = botService.getOrCreateUser(message.from, message.chatId)
			taskService.save(Task(user, task, receiver))
			SendMessage(message.chatId, messages.get("tasks.created", arrayOf(task)))
		}
		else
			SendMessage(message.chatId, messages.get("tasks.create.personal")).setReplyMarkup(ForceReplyKeyboard())
	}

	@MessageMapping("settings")
	fun handleSettingsCommand(message: Message): SendMessage {
		val user = botService.getOrCreateUser(message.from, message.chatId)
		return createSettingsMessage(message.chatId, user)
	}

	private fun createHelpMessage(chatId: Long): SendMessage {
		val replyMarkup = InlineKeyboardMarkup()
		replyMarkup.keyboard = listOf(listOf(
				InlineKeyboardButton(messages.get("tasks.start")).setSwitchInlineQuery("")
		))
		return SendMessage(chatId, messages.get("help")).enableHtml(true).disableWebPagePreview()
				.setReplyMarkup(replyMarkup)
	}

	private fun createSettingsMessage(chatId: Long, user: User): SendMessage {
		val replyMarkup = InlineKeyboardMarkup()
		replyMarkup.keyboard = listOf(listOf(
				InlineKeyboardButton(messages.get("settings.changeLanguage"))
						.setCustomCallbackData(createChangeLanguageCallbackData())
		))
		val messageText = messages.get("settings.currentLanguage", arrayOf(user.language.languageName))
		return SendMessage(chatId, messageText).setReplyMarkup(replyMarkup)
	}

}
