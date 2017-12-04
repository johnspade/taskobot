package ru.johnspade.taskobot.handlers

import org.junit.Test
import org.telegram.telegrambots.api.objects.replykeyboard.ForceReplyKeyboard
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateCommandHandlerTest: UpdateHandlerTest() {

	@Test
	fun handle() {
		val answer = commandHandler.handleCreateCommand(createCommand("/create", aliceTelegram))
		assertNotNull(answer)
		answer.validate()
		assertEquals(messages.get("tasks.create.personal"), answer.text)
		assertTrue(answer.replyMarkup is ForceReplyKeyboard)
	}

}
