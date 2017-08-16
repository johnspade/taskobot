package ru.johnspade.taskobot

import com.fasterxml.jackson.annotation.JsonProperty

enum class CallbackDataType {
	CONFIRM_TASK, USERS, TASKS, CHECK_TASK
}

data class CallbackData(
		@JsonProperty("d")
		val type: CallbackDataType,
		@JsonProperty("p")
		val page: Int? = null,
		@JsonProperty("u")
		val userId: Int? = null,
		@JsonProperty("t")
		val taskId: Long? = null
)