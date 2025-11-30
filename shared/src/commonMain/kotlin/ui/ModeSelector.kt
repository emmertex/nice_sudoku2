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
import domain.HighlightMode
import domain.PlayMode

@Composable
fun ModeSelector(
    highlightMode: HighlightMode,
    playMode: PlayMode,
    onHighlightModeChange: (HighlightMode) -> Unit,
    onPlayModeChange: (PlayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Highlight Mode Section
        Text(
            text = "Highlight Mode",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HighlightModeChip(
                mode = HighlightMode.CELL,
                label = "Cell",
                isSelected = highlightMode == HighlightMode.CELL,
                onClick = { onHighlightModeChange(HighlightMode.CELL) },
                modifier = Modifier.weight(1f)
            )
            HighlightModeChip(
                mode = HighlightMode.RCB_SELECTED,
                label = "RCB Sel",
                isSelected = highlightMode == HighlightMode.RCB_SELECTED,
                onClick = { onHighlightModeChange(HighlightMode.RCB_SELECTED) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HighlightModeChip(
                mode = HighlightMode.RCB_ALL,
                label = "RCB All",
                isSelected = highlightMode == HighlightMode.RCB_ALL,
                onClick = { onHighlightModeChange(HighlightMode.RCB_ALL) },
                modifier = Modifier.weight(1f)
            )
            HighlightModeChip(
                mode = HighlightMode.PENCIL,
                label = "Pencil",
                isSelected = highlightMode == HighlightMode.PENCIL,
                onClick = { onHighlightModeChange(HighlightMode.PENCIL) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Play Mode Section
        Text(
            text = "Play Mode",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PlayModeChip(
                mode = PlayMode.FAST,
                label = "Fast",
                isSelected = playMode == PlayMode.FAST,
                onClick = { onPlayModeChange(PlayMode.FAST) },
                modifier = Modifier.weight(1f)
            )
            PlayModeChip(
                mode = PlayMode.ADVANCED,
                label = "Advanced",
                isSelected = playMode == PlayMode.ADVANCED,
                onClick = { onPlayModeChange(PlayMode.ADVANCED) },
                modifier = Modifier.weight(1f)
            )
        }

        // Mode description
        Text(
            text = when (playMode) {
                PlayMode.FAST -> "Click number → Click cell to fill"
                PlayMode.ADVANCED -> "Click number → Select action"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun HighlightModeChip(
    mode: HighlightMode,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        Color(0xFFBBDEFB) // Light blue
    } else {
        Color(0xFFF5F5F5)
    }

    val borderColor = if (isSelected) {
        Color(0xFF1976D2) // Blue
    } else {
        Color.LightGray
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF1976D2) else Color.Black,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PlayModeChip(
    mode: PlayMode,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        when (mode) {
            PlayMode.FAST -> Color(0xFFE8F5E9) // Light green
            PlayMode.ADVANCED -> Color(0xFFFFF3E0) // Light orange
        }
    } else {
        Color(0xFFF5F5F5)
    }

    val borderColor = if (isSelected) {
        when (mode) {
            PlayMode.FAST -> Color(0xFF4CAF50) // Green
            PlayMode.ADVANCED -> Color(0xFFFF9800) // Orange
        }
    } else {
        Color.LightGray
    }

    val textColor = if (isSelected) {
        when (mode) {
            PlayMode.FAST -> Color(0xFF2E7D32) // Dark green
            PlayMode.ADVANCED -> Color(0xFFE65100) // Dark orange
        }
    } else {
        Color.Black
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Action bar for Advanced play mode - shows Set Number, Remove Pencil options
 */
@Composable
fun AdvancedModeActionBar(
    selectedNumber: Int?,
    canSetNumber: Boolean,
    canRemovePencil: Boolean,
    onSetNumber: () -> Unit,
    onRemovePencil: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedNumber == null) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Selected: $selectedNumber",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Set Number button
        ActionChip(
            text = "Set (Enter)",
            onClick = onSetNumber,
            enabled = canSetNumber,
            backgroundColor = Color(0xFFE3F2FD),
            enabledBorderColor = Color(0xFF1976D2)
        )

        // Remove Pencil button
        ActionChip(
            text = "Remove (Space)",
            onClick = onRemovePencil,
            enabled = canRemovePencil,
            backgroundColor = Color(0xFFFFEBEE),
            enabledBorderColor = Color(0xFFD32F2F)
        )
    }
}

@Composable
private fun ActionChip(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    backgroundColor: Color,
    enabledBorderColor: Color,
    modifier: Modifier = Modifier
) {
    val actualBackgroundColor = if (enabled) backgroundColor else Color(0xFFE0E0E0)
    val actualBorderColor = if (enabled) enabledBorderColor else Color.LightGray
    val textColor = if (enabled) enabledBorderColor else Color.Gray

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(actualBackgroundColor)
            .border(1.dp, actualBorderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

