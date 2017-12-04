package ru.johnspade.taskobot.handlers

import org.junit.Test

class HelpCommandHandlerTest: UpdateHandlerTest() {

	@Test
	fun handle() {
		testHelp(commandHandler.handleHelpCommand(createCommand("/help", aliceTelegram)), messages)
	}

}
