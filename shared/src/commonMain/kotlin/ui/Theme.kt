package ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Good Sudoku inspired color palette
val SudokuPrimary = Color(0xFF1976D2)
val SudokuSecondary = Color(0xFF03DAC6)
val SudokuBackground = Color(0xFFFAFAFA)
val SudokuSurface = Color.White
val SudokuOnPrimary = Color.White
val SudokuOnSecondary = Color.Black
val SudokuOnBackground = Color.Black
val SudokuOnSurface = Color.Black

val SudokuLightColorScheme = lightColorScheme(
    primary = SudokuPrimary,
    secondary = SudokuSecondary,
    background = SudokuBackground,
    surface = SudokuSurface,
    onPrimary = SudokuOnPrimary,
    onSecondary = SudokuOnSecondary,
    onBackground = SudokuOnBackground,
    onSurface = SudokuOnSurface
)

val SudokuDarkColorScheme = darkColorScheme(
    primary = SudokuPrimary,
    secondary = SudokuSecondary,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = SudokuOnPrimary,
    onSecondary = SudokuOnSecondary,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun SudokuTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SudokuDarkColorScheme else SudokuLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}


