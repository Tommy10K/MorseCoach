package com.example.morsecoach

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ListeningPhase {
    LISTEN,         // Play audio, user listens
    TYPE_MORSE,     // User types the morse code they heard
    GUESS_LETTER    // User guesses what letter it was
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningPracticeScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository() }
    val scope = rememberCoroutineScope()
    
    // Get a random character
    fun getRandomChar(): Char {
        return MorseData.letterToCode.keys.random()
    }
    
    // Current character
    var currentChar by remember { mutableStateOf(getRandomChar()) }
    val correctMorse = MorseData.letterToCode[currentChar] ?: ""
    
    // Quiz state
    var phase by remember { mutableStateOf(ListeningPhase.LISTEN) }
    var userMorseInput by remember { mutableStateOf("") }
    var selectedLetter by remember { mutableStateOf<Char?>(null) }
    var isMorseCorrect by remember { mutableStateOf<Boolean?>(null) }
    var isLetterCorrect by remember { mutableStateOf<Boolean?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var streak by remember { mutableIntStateOf(0) }
    var showStreakAnimation by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    
    // All possible characters for letter selection
    val allLetters = MorseData.letterToCode.keys.filter { it.isLetter() }.sorted()
    val allNumbers = MorseData.letterToCode.keys.filter { it.isDigit() }.sorted()
    
    // Play morse audio
    fun playMorseAudio() {
        if (isPlaying) return
        isPlaying = true
        scope.launch {
            transmitMorse(context, correctMorse, "Audio") { }
            isPlaying = false
        }
    }
    
    // Check morse answer
    fun checkMorseAnswer() {
        val correct = userMorseInput.trim() == correctMorse
        isMorseCorrect = correct
        if (correct) {
            // Move to letter guessing phase
            phase = ListeningPhase.GUESS_LETTER
        }
    }
    
    // Check letter answer
    fun checkLetterAnswer(letter: Char) {
        selectedLetter = letter
        val correct = letter == currentChar
        isLetterCorrect = correct
        if (correct && isMorseCorrect == true && !showHint) {
            streak++
            showStreakAnimation = true
            scope.launch {
                authRepo.updateListeningHighStreak(streak)
            }
        } else if (!correct) {
            streak = 0
        }
    }
    
    // Reset for next character
    fun nextCharacter() {
        var next = getRandomChar()
        while (next == currentChar) {
            next = getRandomChar()
        }
        currentChar = next
        phase = ListeningPhase.LISTEN
        userMorseInput = ""
        selectedLetter = null
        isMorseCorrect = null
        isLetterCorrect = null
        showHint = false
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
                title = { Text("Listening Practice") },
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
            // Phase indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PhaseIndicator(
                    number = 1,
                    label = "Listen",
                    isActive = phase == ListeningPhase.LISTEN,
                    isComplete = phase != ListeningPhase.LISTEN
                )
                PhaseIndicator(
                    number = 2,
                    label = "Type Morse",
                    isActive = phase == ListeningPhase.TYPE_MORSE,
                    isComplete = phase == ListeningPhase.GUESS_LETTER
                )
                PhaseIndicator(
                    number = 3,
                    label = "Guess Letter",
                    isActive = phase == ListeningPhase.GUESS_LETTER,
                    isComplete = isLetterCorrect == true
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            when (phase) {
                ListeningPhase.LISTEN -> {
                    // Play button
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
                                text = "🎧",
                                fontSize = 64.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Listen carefully to the Morse code",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = { playMorseAudio() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isPlaying
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isPlaying) "Playing..." else "Play Sound",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = { 
                            phase = ListeningPhase.TYPE_MORSE 
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("I'm ready to type", fontSize = 18.sp)
                    }
                }
                
                ListeningPhase.TYPE_MORSE -> {
                    // Replay button
                    OutlinedButton(
                        onClick = { playMorseAudio() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isPlaying
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isPlaying) "Playing..." else "Replay Sound")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Hint button
                    if (!showHint && isMorseCorrect == null) {
                        OutlinedButton(
                            onClick = { 
                                showHint = true
                                streak = 0
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Show Hint (breaks streak)")
                        }
                    }
                    
                    // Show hint
                    AnimatedVisibility(visible = showHint) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Text(
                                text = "Answer: $correctMorse",
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // User input display
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when (isMorseCorrect) {
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
                                text = "Type what you heard:",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = userMorseInput.ifEmpty { "• —" },
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (isMorseCorrect) {
                                    true -> Color(0xFF4CAF50)
                                    false -> MaterialTheme.colorScheme.error
                                    null -> if (userMorseInput.isEmpty()) 
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.onSurface
                                },
                                letterSpacing = 4.sp
                            )
                            
                            if (isMorseCorrect == false) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = {
                                    userMorseInput = ""
                                    isMorseCorrect = null
                                }) {
                                    Text(
                                        "✗ Try again (tap to clear)",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Input buttons
                    if (isMorseCorrect != true) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(72.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { userMorseInput += "." },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("• Dit", fontSize = 22.sp)
                            }
                            
                            Button(
                                onClick = { userMorseInput += "-" },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("— Dah", fontSize = 22.sp)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { 
                                    if (userMorseInput.isNotEmpty()) {
                                        userMorseInput = userMorseInput.dropLast(1)
                                        isMorseCorrect = null
                                    }
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("⌫ Clear", fontSize = 16.sp)
                            }
                            
                            Button(
                                onClick = { checkMorseAnswer() },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = RoundedCornerShape(16.dp),
                                enabled = userMorseInput.isNotEmpty()
                            ) {
                                Text("Check", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                ListeningPhase.GUESS_LETTER -> {
                    // Success message for morse
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "✓ Correct Morse: $correctMorse",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Now, what letter was that?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Result feedback
                    if (isLetterCorrect != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isLetterCorrect == true) 
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
                                    text = if (isLetterCorrect == true) "✓ Correct! It was $currentChar" else "✗ Wrong! It was: $currentChar",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = if (isLetterCorrect == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
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
                                    isCorrect = isLetterCorrect,
                                    onClick = { checkLetterAnswer(letter) }
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
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
                                    isCorrect = isLetterCorrect,
                                    onClick = { checkLetterAnswer(number) }
                                )
                            }
                        }
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
fun PhaseIndicator(
    number: Int,
    label: String,
    isActive: Boolean,
    isComplete: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(16.dp),
            color = when {
                isComplete -> Color(0xFF4CAF50)
                isActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (isComplete) "✓" else number.toString(),
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isComplete || isActive -> Color.White
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                isComplete -> Color(0xFF4CAF50)
                isActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            },
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}
