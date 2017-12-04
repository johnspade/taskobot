package ru.johnspade.taskobot.handlers

import org.junit.Test

class SettingsCommandHandlerTest: UpdateHandlerTest() {

	@Test
	fun handle() {
		val answer = commandHandler.handleSettingsCommand(createCommand("/settings", aliceTelegram))
		testSettings(answer, alice, messages)
	}

}
