package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import domain.CellHighlightType
import domain.HighlightColour
import domain.SudokuCell

// Highlight colours for number selection
private val PrimaryHighlightColour = Color(0xFFBBDEFB)      // Light blue
private val SecondaryHighlightColour = Color(0xFFFFCDD2)    // Light red
private val IntersectionHighlightColour = Color(0xFFE1BEE7) // Light purple
private val SelectedCellColour = Color(0xFF90CAF9)          // Slightly darker blue for selected cell

@Composable
fun CellView(
    cell: SudokuCell,
    isSelected: Boolean,
    onCellClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlightedCandidates: Set<Int> = emptySet() // Candidates to highlight (for pencil mode)
) {
    // Determine background color based on selection and highlight state
    val backgroundColour = when {
        isSelected -> SelectedCellColour
        // Technique highlighting takes precedence
        cell.isHighlighted -> cell.highlightColour?.let { getTechniqueHighlightColour(it) } ?: Color.White
        // Number selection highlighting
        cell.highlightType != CellHighlightType.NONE -> getSelectionHighlightColour(cell.highlightType)
        cell.isGiven -> Color(0xFFF5F5F5) // Light gray for given cells
        else -> Color.White
    }

    val borderColour = when {
        isSelected -> Color(0xFF1976D2) // Blue border for selected
        cell.isHighlighted -> cell.highlightColour?.let { getTechniqueHighlightColour(it)?.copy(alpha = 0.7f) } ?: Color.Gray
        cell.highlightType != CellHighlightType.NONE -> getSelectionHighlightColour(cell.highlightType).copy(alpha = 0.7f)
        else -> Color.LightGray
    }

    val borderWidth = if (isSelected) 3.dp else 1.dp

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColour)
            .border(borderWidth, borderColour ?: Color.LightGray, RoundedCornerShape(4.dp))
            .clickable(onClick = onCellClick),
        contentAlignment = Alignment.Center
    ) {
        if (cell.isSolved) {
            // Display solved number
            Text(
                text = cell.value.toString(),
                fontSize = 24.sp,
                fontWeight = if (cell.isGiven) FontWeight.Bold else FontWeight.Normal,
                color = if (cell.isGiven) Color.Black else Color(0xFF1976D2), // Blue for user-entered
                textAlign = TextAlign.Center
            )
        } else {
            // Display candidates/pencilmarks (use displayCandidates = candidates - userEliminations)
            CandidatesGrid(
                candidates = cell.displayCandidates,
                highlightedCandidates = highlightedCandidates,
                modifier = Modifier.fillMaxSize().padding(2.dp)
            )
        }
    }
}

@Composable
private fun CandidatesGrid(
    candidates: Set<Int>,
    highlightedCandidates: Set<Int> = emptySet(),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        for (row in 0..2) {
            Row(modifier = Modifier.weight(1f)) {
                for (col in 0..2) {
                    val candidate = (row * 3 + col + 1)
                    val isPresent = candidate in candidates
                    val isHighlighted = candidate in highlightedCandidates

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (isHighlighted && isPresent) {
                                    Modifier.background(
                                        PrimaryHighlightColour.copy(alpha = 0.5f),
                                        RoundedCornerShape(2.dp)
                                    )
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPresent) {
                            Text(
                                text = candidate.toString(),
                                fontSize = 10.sp,
                                color = if (isHighlighted) Color(0xFF1976D2) else Color.DarkGray,
                                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Get the background color for number selection highlighting.
 */
private fun getSelectionHighlightColour(type: CellHighlightType): Color {
    return when (type) {
        CellHighlightType.NONE -> Color.White
        CellHighlightType.PRIMARY -> PrimaryHighlightColour
        CellHighlightType.SECONDARY -> SecondaryHighlightColour
        CellHighlightType.INTERSECTION -> IntersectionHighlightColour
    }
}

/**
 * Get the background color for technique highlighting.
 */
private fun getTechniqueHighlightColour(color: HighlightColour?): Color? {
    return when (color) {
        HighlightColour.RED -> Color(0xFFFFEBEE)
        HighlightColour.BLUE -> Color(0xFFE3F2FD)
        HighlightColour.GREEN -> Color(0xFFE8F5E8)
        HighlightColour.YELLOW -> Color(0xFFFFF9C4)
        HighlightColour.PURPLE -> Color(0xFFF3E5F5)
        HighlightColour.ORANGE -> Color(0xFFFFF3E0)
        HighlightColour.PINK -> Color(0xFFFCE4EC)
        HighlightColour.CYAN -> Color(0xFFE0F2F1)
        null -> null
    }
}
