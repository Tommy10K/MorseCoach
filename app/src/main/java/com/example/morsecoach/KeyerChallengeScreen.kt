package com.example.morsecoach

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

// Shared phrases for both Racer and Keyer modes
object ChallengePhrases {
    val phrases = listOf(
        // Short phrases (good for beginners)
        "SOS",
        "HI",
        "OK",
        "CQ",
        "TEST",
        "HELLO",
        "MORSE",
        "HI THERE",
        "HELLO WORLD",
        "CQ CQ CQ",
        "GOOD MORNING",
        "THANK YOU",
        "WELL DONE",
        "COPY THAT",
        "ROGER THAT",
        "OVER AND OUT",
        // Medium phrases
        "SOS TITANIC",
        "THE QUICK BROWN FOX",
        "MORSE CODE IS FUN",
        "RADIO SILENCE",
        "CALL FOR HELP",
        "SEND BACKUP NOW",
        "MESSAGE RECEIVED",
        "STAND BY PLEASE",
        "REPEAT LAST MESSAGE",
        // Classic/Historic
        "WHAT HATH GOD WROUGHT",
        "COME HERE WATSON",
        "PARIS PARIS PARIS",
        // Ham radio
        "QTH IS HOME",
        "RST FIVE NINE",
        "SEVENTY THREE"
    )
    
    fun getRandomPhrase(excludeCurrent: String? = null): String {
        return phrases.filter { it != excludeCurrent }.random()
    }
    
    fun getRandomPhraseByDifficulty(difficulty: KeyerDifficulty, excludeCurrent: String? = null): String {
        val filtered = when (difficulty) {
            KeyerDifficulty.RELAXED -> phrases.filter { it.length <= 10 }
            KeyerDifficulty.NORMAL -> phrases.filter { it.length in 5..20 }
            KeyerDifficulty.FAST -> phrases.filter { it.contains(' ') } // Only multi-word phrases
        }.filter { it != excludeCurrent }
        return filtered.randomOrNull() ?: phrases.filter { it != excludeCurrent }.random()
    }
}

enum class KeyerDifficulty(
    val displayName: String,
    val ditThreshold: Long,
    val letterSpaceTime: Long,
    val wordSpaceTime: Long,
    val description: String
) {
    RELAXED("Relaxed", 300L, 600L, 1500L, "Slower timing, great for learning"),
    NORMAL("Normal", 200L, 400L, 1000L, "Standard Morse timing"),
    FAST("Fast", 120L, 250L, 600L, "Challenge yourself!")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyerChallengeScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepo = remember { AuthRepository() }
    
    // Difficulty state
    var selectedDifficulty by remember { mutableStateOf(KeyerDifficulty.NORMAL) }
    var showDifficultyDialog by remember { mutableStateOf(false) }
    
    // Audio state
    var toneGenerator by remember { mutableStateOf<ToneGenerator?>(null) }
    
    // Initialize tone generator
    DisposableEffect(Unit) {
        toneGenerator = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
        } catch (e: Exception) {
            null
        }
        onDispose {
            toneGenerator?.release()
        }
    }
    
    // Target phrase to translate
    var targetPhrase by remember { mutableStateOf(ChallengePhrases.getRandomPhraseByDifficulty(selectedDifficulty)) }
    val targetMorse = remember(targetPhrase) {
        targetPhrase.uppercase().map { char ->
            if (char == ' ') "/" else MorseData.letterToCode[char] ?: "?"
        }.joinToString(" ")
    }
    
    // User input state
    var currentMorseInput by remember { mutableStateOf("") }
    var currentLetterInput by remember { mutableStateOf("") }
    
    // Key press state
    var isKeyPressed by remember { mutableStateOf(false) }
    var pressStartTime by remember { mutableStateOf(0L) }
    
    // Audio job for continuous tone while pressing
    var audioJob by remember { mutableStateOf<Job?>(null) }
    
    // Space detection job
    var spaceDetectionJob by remember { mutableStateOf<Job?>(null) }
    
    // Space indicator state for pop-up animation
    var spaceIndicator by remember { mutableStateOf<String?>(null) }
    var spaceIndicatorKey by remember { mutableIntStateOf(0) }
    
    // Decoded text from user input
    val decodedText = remember(currentMorseInput) {
        if (currentMorseInput.isBlank()) return@remember ""
        currentMorseInput.trim().trimEnd('/').trim().split(" / ").joinToString(" ") { word ->
            word.trim().split(" ").filter { it.isNotEmpty() }.map { code ->
                MorseData.codeToLetter[code] ?: '?'
            }.joinToString("")
        }.trim()
    }
    
    // Check if correct
    val isComplete = decodedText.equals(targetPhrase, ignoreCase = true)
    val trimmedMorseInput = currentMorseInput.trim().trimEnd('/').trim()
    val isError = trimmedMorseInput.isNotEmpty() && !targetMorse.startsWith(trimmedMorseInput)
    
    // Animation for key press
    val keyScale by animateFloatAsState(
        targetValue = if (isKeyPressed) 0.92f else 1f,
        label = "keyScale"
    )
    val keyColor by animateColorAsState(
        targetValue = if (isKeyPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        label = "keyColor"
    )
    
    // Function to finalize current letter
    fun finalizeCurrentLetter() {
        if (currentLetterInput.isNotEmpty()) {
            currentMorseInput = if (currentMorseInput.isEmpty()) {
                currentLetterInput
            } else if (currentMorseInput.trimEnd().endsWith("/")) {
                // After word separator, don't add extra space
                "${currentMorseInput.trimEnd()} $currentLetterInput"
            } else {
                "$currentMorseInput $currentLetterInput"
            }
            currentLetterInput = ""
            // Show letter space indicator
            spaceIndicator = "LETTER"
            spaceIndicatorKey++
        }
    }
    
    // Function to add word space
    fun addWordSpace() {
        finalizeCurrentLetter()
        // Clean up any trailing separators first
        val cleaned = currentMorseInput.trim().trimEnd('/').trim()
        if (cleaned.isNotEmpty() && !cleaned.endsWith(" / ")) {
            currentMorseInput = "$cleaned / "
            // Show word space indicator (upgrade from letter)
            spaceIndicator = "WORD"
            spaceIndicatorKey++
        }
    }
    
    // Handle key press
    fun onKeyDown() {
        // Don't accept input if phrase is already complete
        if (isComplete) return
        
        isKeyPressed = true
        pressStartTime = System.currentTimeMillis()
        
        // Cancel any pending space detection
        spaceDetectionJob?.cancel()
        spaceIndicator = null
        
        // Start continuous audio tone
        audioJob = scope.launch {
            while (isActive) {
                toneGenerator?.startTone(ToneGenerator.TONE_DTMF_5, 100)
                delay(90)
            }
        }
    }
    
    // Handle key release
    fun onKeyUp() {
        isKeyPressed = false
        val pressDuration = System.currentTimeMillis() - pressStartTime
        
        // Stop audio
        audioJob?.cancel()
        audioJob = null
        toneGenerator?.stopTone()
        
        // Don't process input if phrase is already complete
        if (isComplete) return
        
        // Determine dit or dah based on press duration and difficulty
        val symbol = if (pressDuration < selectedDifficulty.ditThreshold) "." else "-"
        currentLetterInput += symbol
        
        // Start space detection with difficulty-adjusted timing
        spaceDetectionJob = scope.launch {
            delay(selectedDifficulty.letterSpaceTime)
            // If we reach here, enough time passed for a letter space
            if (!isComplete) finalizeCurrentLetter()
            
            delay(selectedDifficulty.wordSpaceTime - selectedDifficulty.letterSpaceTime)
            // If we reach here, enough time passed for a word space
            if (!isComplete) addWordSpace()
        }
    }
    
    // Difficulty selection dialog
    if (showDifficultyDialog) {
        AlertDialog(
            onDismissRequest = { showDifficultyDialog = false },
            title = { Text("Select Difficulty") },
            text = {
                Column {
                    KeyerDifficulty.entries.forEach { difficulty ->
                        Card(
                            onClick = {
                                selectedDifficulty = difficulty
                                // Reset and get new phrase appropriate for difficulty
                                currentMorseInput = ""
                                currentLetterInput = ""
                                spaceDetectionJob?.cancel()
                                spaceIndicator = null
                                targetPhrase = ChallengePhrases.getRandomPhraseByDifficulty(difficulty)
                                showDifficultyDialog = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedDifficulty == difficulty)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = difficulty.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = difficulty.description,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Dit: <${difficulty.ditThreshold}ms • Letter gap: ${difficulty.letterSpaceTime}ms",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDifficultyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Success dialog
    if (isComplete) {
        // Increment completion counter when phrase is completed
        LaunchedEffect(Unit) {
            authRepo.incrementKeyerCompletion(selectedDifficulty.name)
        }
        
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Correct!") },
            text = { 
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("You successfully transmitted:", style = MaterialTheme.typography.bodyLarge)
                    Text(targetPhrase, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(onClick = {
                    // Reset for next phrase
                    targetPhrase = ChallengePhrases.getRandomPhraseByDifficulty(selectedDifficulty, targetPhrase)
                    currentMorseInput = ""
                    currentLetterInput = ""
                    spaceIndicator = null
                }) {
                    Text("Next Phrase")
                }
            },
            dismissButton = {
                TextButton(onClick = onBackClick) {
                    Text("Exit")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Keyer Mode") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Difficulty button
                    FilledTonalButton(
                        onClick = { showDifficultyDialog = true },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Difficulty",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(selectedDifficulty.displayName)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Target phrase card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Transmit this phrase:",
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = targetPhrase,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = targetMorse,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Current input display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Your Input:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Show current morse with pending letter
                        val displayMorse = if (currentLetterInput.isNotEmpty()) {
                            if (currentMorseInput.isEmpty()) currentLetterInput
                            else "$currentMorseInput $currentLetterInput"
                        } else {
                            currentMorseInput.ifEmpty { "—" }
                        }
                        
                        Text(
                            text = displayMorse,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Decoded text
                        val pendingDecode = if (currentLetterInput.isNotEmpty()) {
                            MorseData.codeToLetter[currentLetterInput]?.toString() ?: "?"
                        } else ""
                        
                        Text(
                            text = "Decoded: ${decodedText}$pendingDecode",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // THE KEY - Large circular button
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .scale(keyScale)
                        .background(keyColor, CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onKeyDown()
                                    tryAwaitRelease()
                                    onKeyUp()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Just a simple indicator dot
                    Box(
                        modifier = Modifier
                            .size(if (isKeyPressed) 60.dp else 40.dp)
                            .background(
                                if (isKeyPressed) MaterialTheme.colorScheme.onPrimary 
                                else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                                CircleShape
                            )
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Clear button
                OutlinedButton(
                    onClick = {
                        currentMorseInput = ""
                        currentLetterInput = ""
                        spaceDetectionJob?.cancel()
                        spaceIndicator = null
                    },
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Text("Clear Input")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Space indicator popup (centered on screen with bounce animation)
            spaceIndicator?.let { indicator ->
                key(spaceIndicatorKey) {
                    SpaceIndicatorPopup(
                        text = indicator,
                        isWordSpace = indicator == "WORD"
                    )
                }
            }
        }
    }
}

@Composable
fun BoxScope.SpaceIndicatorPopup(
    text: String,
    isWordSpace: Boolean
) {
    // Bounce animation
    val animatable = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        // Pop in with overshoot
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
        // Hold briefly
        delay(400)
        // Fade/shrink out
        animatable.animateTo(
            targetValue = 0f,
            animationSpec = tween(200)
        )
    }
    
    val scale = animatable.value
    val alpha = animatable.value
    
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .scale(scale * if (isWordSpace) 1.3f else 1f)
            .background(
                color = if (isWordSpace) 
                    MaterialTheme.colorScheme.tertiary.copy(alpha = alpha * 0.95f)
                else 
                    MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.9f),
                shape = CircleShape
            )
            .padding(horizontal = 32.dp, vertical = 16.dp)
    ) {
        Text(
            text = if (isWordSpace) "WORD SPACE" else "LETTER",
            fontSize = if (isWordSpace) 24.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (isWordSpace) 
                MaterialTheme.colorScheme.onTertiary.copy(alpha = alpha)
            else 
                MaterialTheme.colorScheme.onPrimary.copy(alpha = alpha)
        )
    }
}
