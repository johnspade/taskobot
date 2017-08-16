package ru.johnspade.taskobot.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import ru.johnspade.taskobot.dao.Task

@Repository
private interface TaskRepository : JpaRepository<Task, Long> {

	@Query("""select t from Task t where t.receiver != null and t.done != true and
	((t.sender.id = ?1 and t.receiver.id = ?2) or (t.sender.id = ?2 and t.receiver.id = ?1)) order by t.createdAt desc""")
	fun findBySenderOrReceiver(id1: Int, id2: Int, pageable: Pageable): Page<Task>

}

@Service
class TaskService @Autowired private constructor(private val taskRepository: TaskRepository) {

	fun get(id: Long): Task = taskRepository.getOne(id)

	fun save(task: Task): Task = taskRepository.save(task)

	fun getUserTasks(id1: Int, id2: Int, pageable: Pageable) = taskRepository.findBySenderOrReceiver(id1, id2, pageable)

}
