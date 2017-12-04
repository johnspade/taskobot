package ru.johnspade.taskobot.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import ru.johnspade.taskobot.dao.User

@Repository
interface UserRepository: JpaRepository<User, Int> {

	@Query("""select distinct u from User u, Task t where t.receiver is not null and t.done != true
	and (u = t.receiver or u = t.sender) and (t.receiver.id = ?1 or t.sender.id = ?1) and u.id != ?1 order by u.id""")
	fun getUsersWithTasks(id: Int, pageable: Pageable): Page<User>

}

@Service
class UserService @Autowired constructor(private val userRepository: UserRepository) {

	fun exists(id: Int) = userRepository.exists(id)

	fun get(id: Int): User = userRepository.getOne(id)

	fun save(user: User): User = userRepository.save(user)

	fun getUsersWithTasks(id: Int, pageable: Pageable) = userRepository.getUsersWithTasks(id, pageable)

}
