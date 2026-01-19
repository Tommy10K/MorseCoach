package com.example.morsecoach

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

sealed class LessonStep {
    data class Teach(val char: Char) : LessonStep()
    data class Quiz(val char: Char) : LessonStep()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    lessonId: String,
    viewModel: LessonViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val lessons by viewModel.lessons.collectAsState()
    val lesson = lessons.find { it.id == lessonId }

    // Generate steps
    val steps = remember(lesson) {
        lesson?.content?.split(" ")?.flatMap { str ->
            str.map { char ->
                listOf(LessonStep.Teach(char), LessonStep.Quiz(char))
            }
        }?.flatten() ?: emptyList()
    }

    var currentStepIndex by remember { mutableIntStateOf(0) }
    val currentStep = steps.getOrNull(currentStepIndex)
    
    // Quiz State
    var quizInput by remember { mutableStateOf("") }
    var isQuizCorrect by remember { mutableStateOf<Boolean?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lesson?.title ?: "Lesson") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (lesson == null) {
                if (lessons.isEmpty()) {
                     CircularProgressIndicator()
                } else {
                     Text("Lesson not found")
                }
            } else if (currentStep == null) {
                Text("Lesson Complete!", style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    viewModel.completeLesson(lessonId)
                    onBackClick()
                }) {
                    Text("Finish")
                }
            } else {
                LinearProgressIndicator(
                    progress = (currentStepIndex.toFloat() / steps.size),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(32.dp))

                when (currentStep) {
                    is LessonStep.Teach -> {
                        TeachStep(
                            char = currentStep.char,
                            onNext = { currentStepIndex++ },
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
                                val isCorrect = quizInput.trim() == targetCode
                                isQuizCorrect = isCorrect
                            },
                            onNext = {
                                quizInput = ""
                                isQuizCorrect = null
                                currentStepIndex++
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TeachStep(
    char: Char,
    onNext: () -> Unit,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val code = MorseData.letterToCode[char] ?: "?"
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Learn this character", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = char.toString(), style = MaterialTheme.typography.displayLarge)
        Text(text = code, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = {
            scope.launch {
                transmitMorse(context, code, "Vibrate") { }
            }
        }) {
            Text("Play Sound/Vibration")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Next")
        }
    }
}

@Composable
fun QuizStep(
    char: Char,
    input: String,
    onInputChange: (String) -> Unit,
    isCorrect: Boolean?,
    onSubmit: () -> Unit,
    onNext: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Type the code for:", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = char.toString(), style = MaterialTheme.typography.displayLarge)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Display Input
        Text(
            text = input.ifEmpty { "Tap buttons below" },
            style = MaterialTheme.typography.headlineMedium,
            color = if (isCorrect == true) Color.Green else if (isCorrect == false) Color.Red else MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (isCorrect != true) {
            // Morse Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { onInputChange(input + ".") }) {
                    Text("• (Dit)", fontSize = 24.sp)
                }
                Button(onClick = { onInputChange(input + "-") }) {
                    Text("— (Dah)", fontSize = 24.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { 
                    if (input.isNotEmpty()) onInputChange(input.dropLast(1)) 
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("⌫")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(onClick = onSubmit, enabled = input.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Check")
            }
        } else {
            Text("Correct!", color = Color.Green, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
        }
        
        if (isCorrect == false) {
             Text("Try again", color = Color.Red)
        }
    }
}
