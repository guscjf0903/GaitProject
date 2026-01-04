package com.gait.gaitproject.domain.user.repository

import com.gait.gaitproject.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {

    fun findByEmail(email: String): User?
}


