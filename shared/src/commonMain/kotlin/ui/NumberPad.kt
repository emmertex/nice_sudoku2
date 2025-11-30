package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

// Selection colors matching CellView
private val PrimarySelectionColor = Color(0xFFBBDEFB)      // Light blue
private val SecondarySelectionColor = Color(0xFFFFCDD2)    // Light red
private val PencilModeColor = Color(0xFFE8F5E9)            // Light green for pencil mode

@Composable
fun NumberPad(
    onNumberClick: (Int) -> Unit,
    onEraseClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedNumber1: Int? = null,
    selectedNumber2: Int? = null,
    isPencilMode: Boolean = false,
    onPencilToggle: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Numbers 1-9 in a 3x3 grid
        for (row in 0..2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0..2) {
                    val number = row * 3 + col + 1
                    val selectionState = when {
                        number == selectedNumber1 && number == selectedNumber2 -> NumberSelectionState.BOTH
                        number == selectedNumber1 -> NumberSelectionState.PRIMARY
                        number == selectedNumber2 -> NumberSelectionState.SECONDARY
                        else -> NumberSelectionState.NONE
                    }
                    NumberButton(
                        number = number,
                        onClick = { onNumberClick(number) },
                        modifier = Modifier.weight(1f),
                        selectionState = selectionState,
                        isPencilMode = isPencilMode
                    )
                }
            }
        }

        // Bottom row: Pencil and Erase buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pencil toggle button
            ActionButton(
                text = "✏️",
                onClick = onPencilToggle,
                modifier = Modifier.weight(1f),
                isActive = isPencilMode,
                activeColor = PencilModeColor
            )

            // Erase button
            ActionButton(
                text = "⌫",
                onClick = onEraseClick,
                modifier = Modifier.weight(1f),
                isDestructive = true
            )

            Spacer(modifier = Modifier.weight(1f)) // Empty space for balance
        }
    }
}

private enum class NumberSelectionState {
    NONE,
    PRIMARY,
    SECONDARY,
    BOTH
}

@Composable
private fun NumberButton(
    number: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectionState: NumberSelectionState = NumberSelectionState.NONE,
    isPencilMode: Boolean = false
) {
    val backgroundColor = when (selectionState) {
        NumberSelectionState.NONE -> if (isPencilMode) PencilModeColor.copy(alpha = 0.3f) else Color(0xFFEEEEEE)
        NumberSelectionState.PRIMARY -> PrimarySelectionColor
        NumberSelectionState.SECONDARY -> SecondarySelectionColor
        NumberSelectionState.BOTH -> Color(0xFFE1BEE7) // Light purple for both
    }

    val borderColor = when (selectionState) {
        NumberSelectionState.NONE -> if (isPencilMode) Color(0xFF4CAF50).copy(alpha = 0.5f) else Color.LightGray
        NumberSelectionState.PRIMARY -> Color(0xFF1976D2) // Blue
        NumberSelectionState.SECONDARY -> Color(0xFFD32F2F) // Red
        NumberSelectionState.BOTH -> Color(0xFF7B1FA2) // Purple
    }

    val borderWidth = if (selectionState != NumberSelectionState.NONE) 3.dp else 1.dp

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(backgroundColor, CircleShape)
            .border(borderWidth, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            fontSize = 24.sp,
            fontWeight = if (selectionState != NumberSelectionState.NONE) FontWeight.Bold else FontWeight.Medium,
            color = when (selectionState) {
                NumberSelectionState.NONE -> Color.Black
                NumberSelectionState.PRIMARY -> Color(0xFF1976D2)
                NumberSelectionState.SECONDARY -> Color(0xFFD32F2F)
                NumberSelectionState.BOTH -> Color(0xFF7B1FA2)
            },
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    activeColor: Color = PrimarySelectionColor,
    isDestructive: Boolean = false
) {
    val backgroundColor = when {
        isDestructive -> Color(0xFFFF5722)
        isActive -> activeColor
        else -> Color(0xFFEEEEEE)
    }

    val borderColor = when {
        isDestructive -> Color(0xFFD84315)
        isActive -> Color(0xFF4CAF50)
        else -> Color.LightGray
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(backgroundColor, CircleShape)
            .border(if (isActive) 3.dp else 1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isDestructive) Color.White else Color.Black,
            textAlign = TextAlign.Center
        )
    }
}


