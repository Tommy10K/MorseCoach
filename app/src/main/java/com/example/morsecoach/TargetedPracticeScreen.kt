package com.example.morsecoach

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Targeted Practice: lesson-style Teach→Quiz flow for the user's 3 weakest characters.
 * Uses the same TeachStep / QuizStep composables from LessonScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetedPracticeScreen(onBackClick: () -> Unit) {
    val authRepo = remember { AuthRepository() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Load weak characters
    var weakChars by remember { mutableStateOf<List<Char>?>(null) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val chars = authRepo.getWeakestCharacters(3)
            weakChars = chars
            if (chars.isEmpty()) loadError = true
        }
    }

    // Build lesson steps from weak chars: Teach + Quiz for each
    val steps = remember(weakChars) {
        weakChars?.flatMap { char ->
            listOf(LessonStep.Teach(char), LessonStep.Quiz(char))
        } ?: emptyList()
    }

    var currentStepIndex by remember { mutableIntStateOf(0) }
    val currentStep = steps.getOrNull(currentStepIndex)

    // Quiz state
    var quizInput by remember { mutableStateOf("") }
    var isQuizCorrect by remember { mutableStateOf<Boolean?>(null) }

    // Timing for charStats recording
    var promptStartMs by remember { mutableStateOf(System.currentTimeMillis()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Targeted Practice") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                // Still loading
                weakChars == null && !loadError -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Finding your weak spots...")
                }

                // No data yet
                loadError || weakChars?.isEmpty() == true -> {
                    Text(
                        text = "🎯",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No targeted data yet",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Complete a few practice sessions or racer challenges first so we can find your weakest characters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onBackClick) {
                        Text("Go Back")
                    }
                }

                // All steps done
                currentStep == null && steps.isNotEmpty() -> {
                    Text(
                        text = "✅",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Targeted Practice Complete!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You practiced: ${weakChars?.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onBackClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Finish", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Active lesson flow
                else -> {
                    // Progress bar
                    LinearProgressIndicator(
                        progress = { currentStepIndex.toFloat() / steps.size },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Show which characters are being practiced
                    Text(
                        text = "Practicing: ${weakChars?.joinToString("  ")}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    when (currentStep) {
                        is LessonStep.Teach -> {
                            TeachStep(
                                char = currentStep.char,
                                onNext = {
                                    currentStepIndex++
                                    promptStartMs = System.currentTimeMillis()
                                },
                                context = context,
                                scope = scope
                            )
                        }
                        is LessonStep.Quiz -> {
                            QuizStep(
                                char = currentStep.char,
                                input = quizInput,
                                onInputChange = { quizInput = it },
                                isCorrect = isQuizCorrect,
                                onSubmit = {
                                    val targetCode = MorseData.letterToCode[currentStep.char]
                                    val correct = quizInput.trim() == targetCode
                                    isQuizCorrect = correct

                                    // Record attempt for charStats
                                    val elapsed = System.currentTimeMillis() - promptStartMs
                                    scope.launch {
                                        authRepo.recordCharAttempt(currentStep.char, correct, elapsed)
                                    }
                                },
                                onNext = {
                                    quizInput = ""
                                    isQuizCorrect = null
                                    currentStepIndex++
                                    promptStartMs = System.currentTimeMillis()
                                }
                            )
                        }
                        null -> { /* handled above */ }
                    }
                }
            }
        }
    }
}
