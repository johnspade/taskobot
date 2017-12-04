package ru.johnspade.taskobot.service

import org.apache.commons.text.StringEscapeUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.johnspade.taskobot.BotApiMethodExecutor
import ru.johnspade.taskobot.EmptyCallback
import ru.johnspade.taskobot.PAGE_SIZE
import ru.johnspade.taskobot.createChangeLanguageCallbackData
import ru.johnspade.taskobot.createCheckTaskCallbackData
import ru.johnspade.taskobot.createTasksCallbackData
import ru.johnspade.taskobot.createUsersCallbackData
import ru.johnspade.taskobot.dao.Language
import ru.johnspade.taskobot.dao.User
import ru.johnspade.taskobot.setCustomCallbackData

@Service
class BotService @Autowired constructor(
		private val userService: UserService,
		private val taskService: TaskService,
		private val messages: Messages,
		@Value("\${BOT_TOKEN}") token: String
) {

	private val botId = token.split(":")[0].toInt()

	fun getTasks(id1: Int, id2: Int, page: Int, message: Message, executor: BotApiMethodExecutor?) {
		val user = userService.get(id1)
		val tasks = taskService.getUserTasks(id1, id2, PageRequest(page, PAGE_SIZE))

		val text = StringBuilder("<b>${messages.get("chats.user", arrayOf(getFullUserName(user)))}</b>\n")
		tasks.forEachIndexed { i, (sender, taskText) ->
			text.append("${i + 1}. ${StringEscapeUtils.escapeHtml4(taskText)} <i>– ${sender.firstName}</i>\n")
		}
		text.append("\n<i>${messages.get("tasks.chooseTaskNumber")}</i>")
		val replyMarkup = InlineKeyboardMarkup().setKeyboard(mutableListOf(tasks.mapIndexed { i, task ->
			val callbackData = createCheckTaskCallbackData(task.id, page, id1)
			InlineKeyboardButton("${i + 1}").setCustomCallbackData(callbackData)
		}))
		val keybord = replyMarkup.keyboard
		if (tasks.hasPrevious()) {
			val button = InlineKeyboardButton(messages.get("pages.previous"))
					.setCustomCallbackData(createTasksCallbackData(id1, page - 1))
			keybord.add(listOf(button))
		}
		if (tasks.hasNext()) {
			val button = InlineKeyboardButton(messages.get("pages.next"))
					.setCustomCallbackData(createTasksCallbackData(id1, page + 1))
			keybord.add(listOf(button))
		}
		val callbackData = createUsersCallbackData(0)
		val button = InlineKeyboardButton(messages.get("chats.list")).setCustomCallbackData(callbackData)
		keybord.add(listOf(button))
		executor?.executeAsync(EditMessageText().setChatId(message.chatId).setMessageId(message.messageId)
				.enableHtml(true).setText(text.toString()).setReplyMarkup(replyMarkup), EmptyCallback())
	}

	fun getOrCreateUser(telegramUser: org.telegram.telegrambots.api.objects.User, chatId: Long? = null): User {
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
			val language = if (!telegramUser.languageCode.isNullOrEmpty() && telegramUser.languageCode.startsWith("ru", true))
				Language.RUSSIAN else Language.ENGLISH
			user = userService.save(User(telegramUser.id, telegramUser.firstName, telegramUser.lastName,
					telegramUser.userName, telegramUser.languageCode, chatId, language))
		}
		return user
	}

	fun getFullUserName(user: User): String {
		return if (user.id == botId)
			messages.get("tasks.personal")
		else
			"${user.firstName}${if (user.lastName == null) "" else " ${user.lastName}"}"
	}

	fun createHelpMessage(chatId: Long): SendMessage {
		val replyMarkup = InlineKeyboardMarkup()
		replyMarkup.keyboard = listOf(listOf(
				InlineKeyboardButton(messages.get("tasks.start")).setSwitchInlineQuery("")
		))
		return SendMessage(chatId, messages.get("help")).enableHtml(true)
				.setReplyMarkup(replyMarkup)
	}

	fun createSettingsMessage(chatId: Long, user: User): SendMessage {
		val replyMarkup = InlineKeyboardMarkup()
		replyMarkup.keyboard = listOf(listOf(
				InlineKeyboardButton(messages.get("settings.changeLanguage"))
						.setCustomCallbackData(createChangeLanguageCallbackData())
		))
		val messageText = messages.get("settings.currentLanguage", arrayOf(user.language.languageName))
		return SendMessage(chatId, messageText).setReplyMarkup(replyMarkup)
	}

}