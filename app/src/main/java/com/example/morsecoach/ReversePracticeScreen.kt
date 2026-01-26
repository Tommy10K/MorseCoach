package com.example.morsecoach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReversePracticeScreen(
    isRandomMode: Boolean = true,
    onBackClick: () -> Unit
) {
    val authRepo = remember { AuthRepository() }
    val scope = rememberCoroutineScope()
    
    // Get a random character
    fun getRandomChar(): Char {
        return MorseData.letterToCode.keys.random()
    }
    
    // Current character being practiced
    var currentChar by remember { mutableStateOf(getRandomChar()) }
    val correctMorse = MorseData.letterToCode[currentChar] ?: ""
    
    // Quiz state
    var selectedLetter by remember { mutableStateOf<Char?>(null) }
    var showHint by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf<Boolean?>(null) }
    var streak by remember { mutableIntStateOf(0) }
    var showStreakAnimation by remember { mutableStateOf(false) }
    
    // All possible characters for selection
    val allLetters = MorseData.letterToCode.keys.filter { it.isLetter() }.sorted()
    val allNumbers = MorseData.letterToCode.keys.filter { it.isDigit() }.sorted()
    
    // Reset function for next character
    fun nextCharacter() {
        var next = getRandomChar()
        while (next == currentChar) {
            next = getRandomChar()
        }
        currentChar = next
        selectedLetter = null
        showHint = false
        isCorrect = null
    }
    
    // Check answer
    fun checkAnswer(letter: Char) {
        selectedLetter = letter
        val correct = letter == currentChar
        isCorrect = correct
        if (correct) {
            if (!showHint) {
                streak++
                showStreakAnimation = true
                scope.launch {
                    authRepo.updateReverseHighStreak(streak)
                }
            }
        } else {
            streak = 0
        }
    }
    
    // Streak animation reset
    LaunchedEffect(showStreakAnimation) {
        if (showStreakAnimation) {
            delay(1000)
            showStreakAnimation = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reverse Practice") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (streak > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "🔥 $streak",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Morse code display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "What letter is this?",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = correctMorse,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        letterSpacing = 8.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Hint section
            AnimatedVisibility(
                visible = showHint,
                enter = fadeIn() + scaleIn()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Answer:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = currentChar.toString(),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            
            if (!showHint && isCorrect == null) {
                OutlinedButton(
                    onClick = { 
                        showHint = true
                        streak = 0
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Show Hint")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Result feedback
            if (isCorrect != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCorrect == true) 
                            Color(0xFF4CAF50).copy(alpha = 0.2f) 
                        else 
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isCorrect == true) "✓ Correct!" else "✗ Wrong! It was: $currentChar",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = if (isCorrect == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { nextCharacter() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Next", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                // Letter selection grid
                Text(
                    text = "Tap the correct letter:",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(allLetters) { letter ->
                        LetterButton(
                            letter = letter,
                            isSelected = selectedLetter == letter,
                            isCorrect = isCorrect,
                            onClick = { checkAnswer(letter) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Numbers row
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.height(100.dp)
                ) {
                    items(allNumbers) { number ->
                        LetterButton(
                            letter = number,
                            isSelected = selectedLetter == number,
                            isCorrect = isCorrect,
                            onClick = { checkAnswer(number) }
                        )
                    }
                }
            }
        }
        
        // Streak animation overlay
        if (showStreakAnimation && streak > 1) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val scale by animateFloatAsState(
                    targetValue = if (showStreakAnimation) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "streakScale"
                )
                
                Surface(
                    modifier = Modifier.scale(scale),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = "🔥 $streak Streak!",
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun LetterButton(
    letter: Char,
    isSelected: Boolean,
    isCorrect: Boolean?,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected && isCorrect == true -> Color(0xFF4CAF50)
        isSelected && isCorrect == false -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    Button(
        onClick = onClick,
        modifier = Modifier.aspectRatio(1f),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        contentPadding = PaddingValues(4.dp),
        enabled = isCorrect == null
    ) {
        Text(
            text = letter.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
