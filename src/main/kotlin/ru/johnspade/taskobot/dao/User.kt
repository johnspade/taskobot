package ru.johnspade.taskobot.dao

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "users")
data class User(
		@Column(name = "id")
		@Id
		val id: Int,
		@Column(name = "first_name", nullable = false)
		var firstName: String,
		@Column(name = "last_name")
		var lastName: String? = null,
		@Column(name = "username")
		var username: String? = null,
		@Column(name = "language_code")
		var languageCode: String? = null,
		@Column(name = "chat_id")
		var chatId: Long? = null
)
