package ru.johnspade.taskobot.dao

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "tasks")
data class Task(
		@Column(name = "id")
		@Id
		val id: String,
		@ManyToOne
		@JoinColumn(name = "sender_id", nullable = false)
		val sender: User,
		@ManyToOne
		@JoinColumn(name = "receiver_id")
		var receiver: User?,
		@Column(name = "text", nullable = false)
		val text: String,
		@Column(name = "done", nullable = false)
		var done: Boolean,
		@Column(name = "created_at", nullable = false)
		val createdAt: Long,
		@Column(name = "done_at")
		var doneAt: Long?
)
