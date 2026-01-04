package com.gait.gaitproject.domain.common.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.LastModifiedDate
import java.time.OffsetDateTime

@MappedSuperclass
abstract class BaseTimeEntity : CreatedAtEntity() {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime? = null
        protected set
}


