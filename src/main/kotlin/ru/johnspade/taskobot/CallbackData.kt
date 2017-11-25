package ru.johnspade.taskobot

import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton

enum class CallbackDataType(val code: Int) {

	CONFIRM_TASK(0x0),
	USERS(0x1),
	TASKS(0x2),
	CHECK_TASK(0x3),
	CHANGE_LANGUAGE(0x4);

}

fun getCallbackDataTypeByCode(code: Int): CallbackDataType = CallbackDataType.values().first { it.code == code }

data class CallbackData(
		val type: CallbackDataType,
		val page: Int? = null,
		val userId: Int? = null,
		val taskId: Long? = null
) {
	override fun toString(): String = "${type.code.toString(16)}%${page?: ""}%${userId?: ""}%${taskId?: ""}"
}

fun InlineKeyboardButton.setCustomCallbackData(callbackData: CallbackData): InlineKeyboardButton {
	setCallbackData(callbackData.toString())
	return this
}

fun getCustomCallbackData(data: String): CallbackData {
	val dataParts = data.split("%")
	return CallbackData(
			type = getCallbackDataTypeByCode(dataParts[0].toInt(16)),
			page = dataParts[1].toIntOrNull(),
			userId = dataParts[2].toIntOrNull(),
			taskId = dataParts[3].toLongOrNull()
	)
}

fun CallbackQuery.getCustomCallbackData(): CallbackData = getCustomCallbackData(data)
