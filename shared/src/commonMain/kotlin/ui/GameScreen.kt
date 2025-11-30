package ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import domain.GameState
import domain.HighlightMode
import domain.PlayMode
import domain.SudokuGrid

@Composable
fun GameScreen(
    gameState: GameState,
    onCellClick: (Int) -> Unit,
    onNumberInput: (Int) -> Unit,
    onEraseInput: () -> Unit,
    onTechniqueClick: (String) -> Unit,
    onFindTechniques: () -> Unit,
    onApplyTechnique: () -> Unit,
    onHighlightModeChange: (HighlightMode) -> Unit = {},
    onPlayModeChange: (PlayMode) -> Unit = {},
    onPencilToggle: () -> Unit = {},
    onSetNumberInCell: () -> Unit = {},
    onRemovePencilMark: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    SudokuTheme {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main game area
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Game status
                GameStatusBar(gameState)

                // Sudoku grid
                // In PENCIL highlight mode, highlight candidates that match selected numbers
                val highlightedCandidates = buildSet {
                    if (gameState.highlightMode == HighlightMode.PENCIL) {
                        gameState.selectedNumber1?.let { add(it) }
                        gameState.selectedNumber2?.let { add(it) }
                    }
                }
                
                SudokuGrid(
                    grid = gameState.grid,
                    selectedCell = gameState.selectedCell,
                    onCellClick = onCellClick,
                    modifier = Modifier.weight(1f),
                    highlightedCandidates = highlightedCandidates
                )

                // Advanced mode action bar (shown when a number is selected in advanced mode)
                if (gameState.playMode == PlayMode.ADVANCED && gameState.selectedNumber1 != null) {
                    AdvancedModeActionBar(
                        selectedNumber = gameState.selectedNumber1,
                        canSetNumber = gameState.selectedCell != null && !gameState.grid.getCell(gameState.selectedCell!!).isGiven,
                        canRemovePencil = gameState.selectedCell != null && 
                            gameState.selectedNumber1 in gameState.grid.getCell(gameState.selectedCell!!).candidates,
                        onSetNumber = onSetNumberInCell,
                        onRemovePencil = onRemovePencilMark
                    )
                }

                // Number pad with selection state
                NumberPad(
                    onNumberClick = onNumberInput,
                    onEraseClick = onEraseInput,
                    selectedNumber1 = gameState.selectedNumber1,
                    selectedNumber2 = gameState.selectedNumber2,
                    isPencilMode = gameState.isPencilMode,
                    onPencilToggle = onPencilToggle
                )

                // Game controls
                GameControls(
                    gameState = gameState,
                    onFindTechniques = onFindTechniques,
                    onApplyTechnique = onApplyTechnique
                )
            }

            // Right side panel with mode selectors and technique panel
            Column(
                modifier = Modifier.width(280.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mode selector
                ModeSelector(
                    highlightMode = gameState.highlightMode,
                    playMode = gameState.playMode,
                    onHighlightModeChange = onHighlightModeChange,
                    onPlayModeChange = onPlayModeChange
                )

                // Selection info
                SelectionInfo(gameState)

                // Technique panel
                TechniquePanel(
                    techniques = gameState.matches,
                    selectedTechnique = gameState.selectedTechnique,
                    onTechniqueClick = onTechniqueClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GameStatusBar(gameState: GameState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when {
                gameState.isSolved -> "🎉 Solved!"
                gameState.isLoading -> "Thinking..."
                gameState.error != null -> "Error: ${gameState.error}"
                else -> "Good Sudoku"
            },
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show pencil mode indicator
            if (gameState.isPencilMode) {
                Text(
                    text = "✏️ Pencil",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "Difficulty: ${gameState.difficulty.name.lowercase().replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SelectionInfo(gameState: GameState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Selection",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Primary number
                Column {
                    Text(
                        text = "Primary",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = gameState.selectedNumber1?.toString() ?: "-",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // Secondary number
                Column {
                    Text(
                        text = "Secondary",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = gameState.selectedNumber2?.toString() ?: "-",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // Selected cell
                Column {
                    Text(
                        text = "Cell",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = gameState.selectedCell?.let { 
                            "R${it / 9 + 1}C${it % 9 + 1}" 
                        } ?: "-",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun GameControls(
    gameState: GameState,
    onFindTechniques: () -> Unit,
    onApplyTechnique: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        OutlinedButton(onClick = { /* TODO: Implement undo */ }) {
            Text("Undo")
        }

        OutlinedButton(onClick = onFindTechniques) {
            Text("Find Techniques")
        }

        OutlinedButton(
            onClick = onApplyTechnique,
            enabled = gameState.selectedMatch != null
        ) {
            Text("Apply")
        }

        OutlinedButton(onClick = { /* TODO: Implement solve */ }) {
            Text("Solve")
        }
    }
}
