package com.example.morsecoach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Refresh
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeQuizScreen(
    character: Char?,
    isRandomMode: Boolean = false,
    onBackClick: () -> Unit
) {
    val authRepo = remember { AuthRepository() }
    val scope = rememberCoroutineScope()
    
    // Current character being practiced
    var currentChar by remember { 
        mutableStateOf(character ?: MorseData.letterToCode.keys.random()) 
    }
    val correctMorse = MorseData.letterToCode[currentChar] ?: ""
    
    // Quiz state
    var userInput by remember { mutableStateOf("") }
    var showHint by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf<Boolean?>(null) }
    var streak by remember { mutableIntStateOf(0) }
    var showStreakAnimation by remember { mutableStateOf(false) }
    
    // Timing for charStats
    var promptStartMs by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // Reset function for next character
    fun nextCharacter() {
        if (isRandomMode) {
            var next = MorseData.letterToCode.keys.random()
            // Avoid same character twice in a row
            while (next == currentChar) {
                next = MorseData.letterToCode.keys.random()
            }
            currentChar = next
        }
        userInput = ""
        showHint = false
        isCorrect = null
        promptStartMs = System.currentTimeMillis()
    }
    
    // Check answer
    fun checkAnswer() {
        val correct = userInput.trim() == correctMorse
        isCorrect = correct
        
        // Record char stats for targeted practice
        val elapsed = System.currentTimeMillis() - promptStartMs
        scope.launch {
            authRepo.recordCharAttempt(currentChar, correct, elapsed)
        }
        
        if (correct) {
            // Only increment streak if in random mode and hint wasn't shown
            if (isRandomMode && !showHint) {
                streak++
                showStreakAnimation = true
                // Update high streak in background
                scope.launch {
                    authRepo.updatePracticeHighStreak(streak)
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
                title = { 
                    Text(if (isRandomMode) "Random Practice" else "Practice: $currentChar") 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Streak display
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
            // Character display
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
                        text = "What is the Morse code for:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = currentChar.toString(),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
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
                            text = correctMorse,
                            fontSize = 32.sp,
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
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // User input display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (isCorrect) {
                        true -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                        false -> MaterialTheme.colorScheme.errorContainer
                        null -> MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your Answer:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = userInput.ifEmpty { "Tap buttons below" },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (isCorrect) {
                            true -> Color(0xFF4CAF50)
                            false -> MaterialTheme.colorScheme.error
                            null -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    
                    // Result message
                    if (isCorrect != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isCorrect == false) {
                            TextButton(
                                onClick = {
                                    userInput = ""
                                    isCorrect = null
                                }
                            ) {
                                Text(
                                    text = "✗ Try again (tap to clear)",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            Text(
                                text = "✓ Correct!",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Morse input buttons (only if not answered correctly)
            if (isCorrect != true) {
                // Dit and Dah buttons
                Row(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { userInput += "." },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("• Dit", fontSize = 24.sp)
                    }
                    
                    Button(
                        onClick = { userInput += "-" },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("— Dah", fontSize = 24.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Backspace and Check buttons
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { 
                            if (userInput.isNotEmpty()) {
                                userInput = userInput.dropLast(1)
                                isCorrect = null // Reset result when editing
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("⌫ Clear", fontSize = 18.sp)
                    }
                    
                    Button(
                        onClick = { checkAnswer() },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = userInput.isNotEmpty()
                    ) {
                        Text("Check", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Next button when correct
                Button(
                    onClick = { nextCharacter() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isRandomMode) "Next Character" else "Try Again",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
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
