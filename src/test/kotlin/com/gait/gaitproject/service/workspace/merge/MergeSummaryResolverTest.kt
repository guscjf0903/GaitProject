package com.gait.gaitproject.service.workspace.merge

import com.gait.gaitproject.domain.common.enums.MergeType
import com.gait.gaitproject.service.workspace.BranchMaterial
import com.gait.gaitproject.service.workspace.MergeContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MergeSummaryResolverTest {
    private val resolver = MergeSummaryResolver(mergeSummaryAiService = null)

    @Test
    fun `squash merge falls back to readable summary when ai is unavailable`() {
        val context = sampleContext(
            fromMaterial = BranchMaterial(
                branchName = "feature/refactor",
                rawMessageExcerpt = "대화 발췌",
                shortSummaries = "- Chat 서비스 분리\n- 권한 체크 추가",
                longSummaries = "상세 요약 본문",
            ),
        )

        val resolved = resolver.resolve(MergeType.SQUASH, context, userNote = null)

        assertEquals("Merged from feature/refactor", resolved.keyPoint)
        assertTrue(resolved.shortSummary!!.contains("Squash merged"))
        assertTrue(resolved.longSummary!!.contains("[feature/refactor → main Squash Merge]"))
        assertTrue(resolved.longSummary!!.contains("상세 요약 본문"))
    }

    @Test
    fun `deep merge keeps nullable summaries when ai is unavailable`() {
        val resolved = resolver.resolve(MergeType.DEEP, sampleContext(), userNote = "중복 제거")

        assertEquals("Deep Merged from feature/refactor", resolved.keyPoint)
        assertNull(resolved.shortSummary)
        assertNull(resolved.longSummary)
    }

    private fun sampleContext(
        fromMaterial: BranchMaterial = BranchMaterial(
            branchName = "feature/refactor",
            rawMessageExcerpt = "",
            shortSummaries = "",
            longSummaries = "",
        ),
        toMaterial: BranchMaterial = BranchMaterial(
            branchName = "main",
            rawMessageExcerpt = "",
            shortSummaries = "",
            longSummaries = "",
        ),
    ): MergeContext =
        MergeContext(
            fromMaterial = fromMaterial,
            toMaterial = toMaterial,
            commonAncestor = null,
            fromPath = emptyList(),
            toPath = emptyList(),
        )
}
