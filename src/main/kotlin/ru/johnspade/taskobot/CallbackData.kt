package ru.johnspade.taskobot

enum class CallbackDataType {
	CONFIRM_TASK, USERS, TASKS, CHECK_TASK
}

data class CallbackData(val type: CallbackDataType, val page: Int? = null, val uid: Int? = null, val tid: String? = null)