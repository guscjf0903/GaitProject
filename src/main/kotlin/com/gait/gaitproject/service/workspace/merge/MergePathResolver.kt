package com.gait.gaitproject.service.workspace.merge

import com.gait.gaitproject.domain.workspace.entity.Commit
import org.springframework.stereotype.Component
import java.util.UUID

data class MergePaths(
    val commonAncestor: Commit?,
    val fromPath: List<Commit>,
    val toPath: List<Commit>,
)

@Component
class MergePathResolver {
    fun resolve(fromHead: Commit?, toHead: Commit?): MergePaths {
        val toAncestors = mutableSetOf<UUID>()
        var currentTo = toHead
        while (currentTo != null) {
            toAncestors.add(currentTo.id!!)
            currentTo = currentTo.parent
        }

        var commonAncestor: Commit? = null
        val fromPath = mutableListOf<Commit>()
        var currentFrom = fromHead
        while (currentFrom != null) {
            if (toAncestors.contains(currentFrom.id!!)) {
                commonAncestor = currentFrom
                break
            }
            fromPath.add(currentFrom)
            currentFrom = currentFrom.parent
        }
        fromPath.reverse()

        val toPath = mutableListOf<Commit>()
        var current = toHead
        while (current != null && current.id != commonAncestor?.id) {
            toPath.add(current)
            current = current.parent
        }
        toPath.reverse()

        return MergePaths(
            commonAncestor = commonAncestor,
            fromPath = fromPath,
            toPath = toPath,
        )
    }
}
