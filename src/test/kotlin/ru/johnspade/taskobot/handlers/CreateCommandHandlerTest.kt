package ru.johnspade.taskobot.handlers

import org.junit.Test
import org.telegram.telegrambots.api.objects.replykeyboard.ForceReplyKeyboard
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateCommandHandlerTest: UpdateHandlerTest() {

	@Test
	fun handleCreateCommand() {
		val answer = commandHandler.handleCreateCommand(createCommand("/create", aliceTelegram))
		assertNotNull(answer)
		answer.validate()
		assertEquals(messages.get("tasks.create.personal"), answer.text)
		assertTrue(answer.replyMarkup is ForceReplyKeyboard)
	}

	@Test
	fun fastCreatePersonalTask() {
		val taskText = "fast personal task"
		val answer = commandHandler.handleCreateCommand(createCommand("/create $taskText", aliceTelegram))
		answer.validate()
		assertNotNull(answer)
		val task = taskRepository.findAll().first { it.sender == alice && it.text == taskText }
		assertNotNull(task.createdAt)
		assertEquals(messages.get("tasks.created", arrayOf(task.text)), answer.text)
	}

}
