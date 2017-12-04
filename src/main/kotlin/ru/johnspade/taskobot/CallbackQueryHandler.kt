package ru.johnspade.taskobot

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.api.objects.Update
import ru.johnspade.taskobot.service.BotService
import ru.johnspade.taskobot.service.LocaleService

@Service
class CallbackQueryHandler @Autowired constructor(
		private val localeService: LocaleService,
		private val botService: BotService
) {

	private val botControllerContainer = BotControllerContainer

	fun handle(executor: BotApiMethodExecutor, update: Update): AnswerCallbackQuery {
		val callbackQuery = update.callbackQuery
		val user = botService.getOrCreateUser(callbackQuery.from)
		localeService.setLocale(user.language)
		val data = callbackQuery.getCustomCallbackData()
		val controller = botControllerContainer.getCallbackQueryController(data.type.code)
		return controller?.process(executor, update) as AnswerCallbackQuery
	}

}
