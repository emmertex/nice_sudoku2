package service.hint.helpers

import dto.EliminationDto

/** Keep each removed digit attached to its own cells, never their cross product. */
fun formatEliminationActions(eliminations: List<EliminationDto>): String =
    eliminations.joinToString("\n") { elimination ->
        "• ${elimination.digit}: ${elimination.cells.distinct().joinToString(", ") { formatCellName(it) }}"
    }
