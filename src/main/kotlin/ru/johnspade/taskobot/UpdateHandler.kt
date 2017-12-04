package ru.johnspade.taskobot

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.api.objects.User as TelegramUser

const val PAGE_SIZE = 5

@Service
class UpdateHandler @Autowired constructor(
		private val inlineQueryHandler: InlineQueryHandler,
		private val chosenInlineQueryHandler: ChosenInlineQueryHandler,
		private val callbackQueryHandler: CallbackQueryHandler,
		private val messageHandler: MessageHandler
) {

	fun handle(executor: BotApiMethodExecutor, update: Update): BotApiMethod<*>? {
		return with(update) {
			when {
				hasInlineQuery() && inlineQuery.hasQuery() -> inlineQueryHandler.handle(inlineQuery)
				hasChosenInlineQuery() -> chosenInlineQueryHandler.handle(chosenInlineQuery)
				hasCallbackQuery() -> callbackQueryHandler.handle(executor, update)
				hasMessage() -> messageHandler.handle(executor, update)
				else -> null
			}
		}
	}

}
