package ru.johnspade.taskobot

import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton

const val CONFIRM_TASK_CODE = 0x0
const val USERS_CODE = 0x1
const val TASKS_CODE = 0x2
const val CHECK_TASK_CODE = 0x3
const val CHANGE_LANGUAGE_CODE = 0x4

enum class CallbackDataType(val code: Int) {

	CONFIRM_TASK(CONFIRM_TASK_CODE),
	USERS(USERS_CODE),
	TASKS(TASKS_CODE),
	CHECK_TASK(CHECK_TASK_CODE),
	CHANGE_LANGUAGE(CHANGE_LANGUAGE_CODE)

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

fun createConfirmTaskCallbackData(taskId: Long? = null) = CallbackData(CallbackDataType.CONFIRM_TASK, taskId = taskId)

fun createUsersCallbackData(page: Int) = CallbackData(CallbackDataType.USERS, page = page)

fun createTasksCallbackData(userId: Int, page: Int) = CallbackData(CallbackDataType.TASKS, userId = userId, page = page)

fun createCheckTaskCallbackData(taskId: Long, page: Int, userId: Int) =
		CallbackData(CallbackDataType.CHECK_TASK, taskId = taskId, page = page, userId = userId)

fun createChangeLanguageCallbackData() = CallbackData(CallbackDataType.CHANGE_LANGUAGE)

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
