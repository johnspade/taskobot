package ru.johnspade.taskobot

import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.johnspade.taskobot.dao.Language

const val CONFIRM_TASK_CODE = 0x0
const val USERS_CODE = 0x1
const val TASKS_CODE = 0x2
const val CHECK_TASK_CODE = 0x3
const val CHANGE_LANGUAGE_CODE = 0x4
const val SET_LANGUAGE_CODE = 0x5

enum class CallbackDataType(val code: Int) {

	CONFIRM_TASK(CONFIRM_TASK_CODE),
	USERS(USERS_CODE),
	TASKS(TASKS_CODE),
	CHECK_TASK(CHECK_TASK_CODE),
	CHANGE_LANGUAGE(CHANGE_LANGUAGE_CODE),
	SET_LANGUAGE(SET_LANGUAGE_CODE)

}

fun getCallbackDataTypeByCode(code: Int): CallbackDataType = CallbackDataType.values().first { it.code == code }

sealed class CbData {
	abstract val type: CallbackDataType
}

data class CallbackData(
		override val type: CallbackDataType,
		val page: Int? = null,
		val userId: Int? = null,
		val taskId: Long? = null
): CbData() {
	override fun toString(): String = "${type.code.toString(16)}%${page?: ""}%${userId?: ""}%${taskId?: ""}"
}

data class SetLanguage(val language: Language): CbData() {
	override val type: CallbackDataType
		get() = CallbackDataType.SET_LANGUAGE

	override fun toString(): String = "${type.code.toString(16)}%${language.languageTag}"
}

fun createConfirmTaskCallbackData(taskId: Long? = null) = CallbackData(CallbackDataType.CONFIRM_TASK, taskId = taskId)

fun createUsersCallbackData(page: Int) = CallbackData(CallbackDataType.USERS, page = page)

fun createTasksCallbackData(userId: Int, page: Int) = CallbackData(CallbackDataType.TASKS, userId = userId, page = page)

fun createCheckTaskCallbackData(taskId: Long, page: Int, userId: Int) =
		CallbackData(CallbackDataType.CHECK_TASK, taskId = taskId, page = page, userId = userId)

fun createChangeLanguageCallbackData() = CallbackData(CallbackDataType.CHANGE_LANGUAGE)

fun createSetLanguage(language: Language) = SetLanguage(language)

fun InlineKeyboardButton.setCustomCallbackData(callbackData: CbData): InlineKeyboardButton {
	setCallbackData(callbackData.toString())
	return this
}

fun getCustomCallbackData(data: String): CbData {
	val dataParts = data.split("%")
	val type = getCallbackDataTypeByCode(dataParts[0].toInt(16))
	return when (type) {
		CallbackDataType.SET_LANGUAGE -> {
			val tag = dataParts.getOrElse(1) { "en" }
			val language = Language.values().first { it.languageTag == tag }
			SetLanguage(language)
		}
		else -> CallbackData(
				type = type,
				page = dataParts[1].toIntOrNull(),
				userId = dataParts[2].toIntOrNull(),
				taskId = dataParts[3].toLongOrNull()
		)
	}
}

fun CallbackQuery.getCustomCallbackData(): CbData = getCustomCallbackData(data)
