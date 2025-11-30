package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TechniquePanel(
    techniques: Map<String, List<Any>>,
    selectedTechnique: String?,
    onTechniqueClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Techniques",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(techniques.keys.sorted()) { techniqueName ->
                val matches = techniques[techniqueName] ?: emptyList()
                TechniqueItem(
                    techniqueName = techniqueName,
                    matchCount = matches.size,
                    isSelected = selectedTechnique == techniqueName,
                    onClick = { onTechniqueClick(techniqueName) }
                )
            }
            }
        }
    }
}

@Composable
private fun TechniqueItem(
    techniqueName: String,
    matchCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = techniqueName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        if (matchCount > 0) {
            @OptIn(ExperimentalMaterial3Api::class)
            Badge(
                content = {
                    Text(
                        text = matchCount.toString(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            )
        }
    }
}
