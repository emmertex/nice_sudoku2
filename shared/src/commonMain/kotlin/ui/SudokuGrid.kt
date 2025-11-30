package ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import domain.SudokuGrid

@Composable
fun SudokuGrid(
    grid: SudokuGrid,
    selectedCell: Int?,
    onCellClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    highlightedCandidates: Set<Int> = emptySet() // Candidates to highlight in all cells (for PENCIL mode)
) {
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        for (row in 0..8) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                for (col in 0..8) {
                    val cellIndex = row * 9 + col
                    val cell = grid.getCell(cellIndex)
                    val isSelected = selectedCell == cellIndex

                    CellView(
                        cell = cell,
                        isSelected = isSelected,
                        onCellClick = { onCellClick(cellIndex) },
                        modifier = Modifier.weight(1f),
                        highlightedCandidates = highlightedCandidates
                    )
                }
            }
        }
    }
}


