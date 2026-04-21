package com.gait.gaitproject.service.workspace.merge

import com.gait.gaitproject.domain.common.enums.MergeType
import com.gait.gaitproject.domain.common.enums.PlanType
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MergePolicyValidatorTest {
    private val validator = MergePolicyValidator()

    @Test
    fun `free plan cannot use merge`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            validator.validate(PlanType.FREE, MergeType.SQUASH)
        }

        assertTrue(exception.message!!.contains("Free 플랜"))
    }

    @Test
    fun `standard plan cannot use deep merge`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            validator.validate(PlanType.STANDARD, MergeType.DEEP)
        }

        assertTrue(exception.message!!.contains("Deep Merge"))
    }

    @Test
    fun `master plan can use deep merge`() {
        validator.validate(PlanType.MASTER, MergeType.DEEP)
    }

    @Test
    fun `fast forward merge is rejected`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            validator.validate(PlanType.MASTER, MergeType.FAST_FORWARD)
        }

        assertTrue(exception.message!!.contains("Fast-Forward"))
    }
}
