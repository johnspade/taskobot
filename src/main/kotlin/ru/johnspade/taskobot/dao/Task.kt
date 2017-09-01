package ru.johnspade.taskobot.dao

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "tasks")
data class Task(
		@ManyToOne
		@JoinColumn(name = "sender_id", nullable = false)
		val sender: User,
		@Column(name = "text", nullable = false)
		val text: String,
		@ManyToOne
		@JoinColumn(name = "receiver_id")
		var receiver: User? = null,
		@Column(name = "created_at", nullable = false)
		val createdAt: Long = System.currentTimeMillis(),
		@Column(name = "done_at")
		var doneAt: Long? = null,
		@Column(name = "done", nullable = false)
		var done: Boolean = false
) {

	@Column(name = "id")
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long = 0

}
