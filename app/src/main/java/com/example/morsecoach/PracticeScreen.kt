package com.example.morsecoach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    onCharacterClick: (Char) -> Unit,
    onRandomClick: () -> Unit,
    onBackClick: () -> Unit
) {
    // Practice progress stored in memory (could be persisted later)
    // Maps character to mastery level: 0 = not tried, 1 = attempted, 2 = mastered
    val practiceProgress = remember { mutableStateMapOf<Char, Int>() }
    
    val characters = MorseData.letterToCode.keys.toList().sorted()
    val letters = characters.filter { it.isLetter() }
    val numbers = characters.filter { it.isDigit() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Practice") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Random practice button
            Button(
                onClick = onRandomClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Random Practice",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Instructions
            Text(
                text = "Tap a character to practice. Test yourself without seeing the answer!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Letters section
            Text(
                text = "Letters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(letters) { char ->
                    PracticeGridItem(
                        char = char,
                        masteryLevel = practiceProgress[char] ?: 0,
                        onClick = { onCharacterClick(char) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Numbers section
            Text(
                text = "Numbers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(140.dp)
            ) {
                items(numbers) { char ->
                    PracticeGridItem(
                        char = char,
                        masteryLevel = practiceProgress[char] ?: 0,
                        onClick = { onCharacterClick(char) }
                    )
                }
            }
        }
    }
}

@Composable
fun PracticeGridItem(
    char: Char,
    masteryLevel: Int,
    onClick: () -> Unit
) {
    val backgroundColor = when (masteryLevel) {
        2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) // Mastered
        1 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f) // Attempted
        else -> MaterialTheme.colorScheme.surfaceVariant // Not tried
    }
    
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = char.toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
