package ru.johnspade.taskobot

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Update
import ru.johnspade.taskobot.dao.Task
import ru.johnspade.taskobot.service.BotService
import ru.johnspade.taskobot.service.LocaleService
import ru.johnspade.taskobot.service.Messages
import ru.johnspade.taskobot.service.TaskService
import ru.johnspade.taskobot.service.UserService

@Service
class MessageHandler @Autowired constructor(
		private val userService: UserService,
		private val taskService: TaskService,
		private val messages: Messages,
		private val localeService: LocaleService,
		@Value("\${BOT_TOKEN}") token: String,
		private val botService: BotService,
		private val botControllerContainer: BotControllerContainer
) {

	private val botId = token.split(":")[0].toInt()

	fun handle(executor: BotApiMethodExecutor, update: Update): SendMessage? {
		val message = update.message
		val user = botService.getOrCreateUser(message.from, message.chatId)
		localeService.setLocale(user.language)
		if (message.isReply && message.hasText()) {
			val replyToMessage = message.replyToMessage
			if (replyToMessage.hasText() && replyToMessage.text.startsWith("/create:")) {
				val receiver = userService.get(botId)
				taskService.save(Task(user, message.text, receiver))
				return SendMessage(message.chatId, messages.get("tasks.created", arrayOf(message.text)))
			}
		}
		else if (message.isCommand) {
			val command = message.text.split(" ")[0].trim().removePrefix("/")
			val controller = botControllerContainer.getMessageController(command)
			return controller?.process(executor, update) as SendMessage?
		}
		return null
	}

}
