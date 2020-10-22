package ru.johnspade.taskobot.handlers

import org.junit.Test
import org.springframework.data.domain.PageRequest
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import ru.johnspade.taskobot.CallbackData
import ru.johnspade.taskobot.CallbackDataType
import ru.johnspade.taskobot.dao.Task
import ru.johnspade.taskobot.dao.User
import ru.johnspade.taskobot.getCustomCallbackData
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ListCommandHandlerTest: UpdateHandlerTest() {

	@Test
	fun returnChatsListFirstPageOnePage() {
		val task = taskService.save(Task(bob, "task", alice))
		val answer = commandHandler.handleListCommand(emptyExecutor, createCommand("/list", aliceTelegram))
		assertNotNull(answer)
		answer.validate()
		assertEquals(messages.get("chats.count", arrayOf(1)), answer.text)
		val inlineKeybord = answer.replyMarkup as InlineKeyboardMarkup
		assertEquals(1, inlineKeybord.keyboard.size)
		assertEquals(1, inlineKeybord.keyboard[0].size)
		val inlineKeybordButton = inlineKeybord.keyboard[0][0]
		assertEquals(bob.firstName, inlineKeybordButton.text)
		val callbackData = getCustomCallbackData(inlineKeybordButton.callbackData) as CallbackData
		assertEquals(CallbackDataType.TASKS, callbackData.type)
		assertEquals(bob.id, callbackData.userId)
		taskRepository.delete(task)
	}

	@Test
	fun returnChatsListFirstPageMultiplePages() {
		val testUsers = mutableListOf<User>()
		val testTasks = mutableListOf<Task>()
		(0..5).forEach {
			val user = userService.save(User(1000 + it + 1, "testUser${it + 1}"))
			testUsers.add(user)
			testTasks.add(taskService.save(Task(user, "task", alice)))
		}
		val answer = commandHandler.handleListCommand(emptyExecutor, createCommand("/list", aliceTelegram))
		assertNotNull(answer)
		answer.validate()
		val usersWithTasks = userService.getUsersWithTasks(alice.id, PageRequest(0, 5))
		assertEquals(
				messages.get("chats.count", arrayOf(usersWithTasks.totalElements)),
				answer.text
		)
		val inlineKeyboard = answer.replyMarkup as InlineKeyboardMarkup
		assertEquals(6, inlineKeyboard.keyboard.size)
		(0..4).forEach {
			assertEquals(1, inlineKeyboard.keyboard[it].size)
			val inlineKeybordButton = inlineKeyboard.keyboard[it][0]
			val user = usersWithTasks.content[it]
			assertEquals(user.firstName, inlineKeybordButton.text)
			val callbackData = getCustomCallbackData(inlineKeybordButton.callbackData) as CallbackData
			assertEquals(CallbackDataType.TASKS, callbackData.type)
			assertEquals(user.id, callbackData.userId)
			assertEquals(0, callbackData.page)
		}
		val inlineKeybordButton = inlineKeyboard.keyboard[5][0]
		assertEquals(messages.get("pages.next"), inlineKeybordButton.text)
		val callbackData = getCustomCallbackData(inlineKeybordButton.callbackData) as CallbackData
		assertEquals(CallbackDataType.USERS, callbackData.type)
		assertEquals(1, callbackData.page)
		taskRepository.delete(testTasks)
		userRepository.delete(testUsers)
	}

}
