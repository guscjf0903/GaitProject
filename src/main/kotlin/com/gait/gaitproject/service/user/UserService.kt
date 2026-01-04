package com.gait.gaitproject.service.user

import com.gait.gaitproject.domain.user.entity.User
import com.gait.gaitproject.domain.user.repository.UserRepository
import com.gait.gaitproject.service.common.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository
) {
    fun get(userId: UUID): User =
        userRepository.findById(userId).orElseThrow {
            NotFoundException("User not found. id=$userId")
        }

    fun getByEmail(email: String): User =
        userRepository.findByEmail(email) ?: throw NotFoundException("User not found. email=$email")

    @Transactional
    fun createIfNotExists(email: String, name: String?): User {
        val existing = userRepository.findByEmail(email)
        if (existing != null) return existing
        return userRepository.save(User(email = email, name = name))
    }
}


