package com.example.morsecoach

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // --- State ---
    var isMorseInput by remember { mutableStateOf(true) } // true = Morse -> Text
    var inputText by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf("Vibrate") } // "Vibrate" or "Flash"

    // Transmission State
    var transmissionJob by remember { mutableStateOf<Job?>(null) }
    var isTransmitting by remember { mutableStateOf(false) }
    var activeCharIndex by remember { mutableStateOf(-1) } // Index of char being played

    var isDropdownExpanded by remember { mutableStateOf(false) }

    // --- Translation Logic ---
    // Updated to handle " / " as a word separator
    val translatedText = remember(inputText, isMorseInput) {
        if (inputText.isBlank()) return@remember ""
        if (isMorseInput) {
            // Translate Morse -> Text
            // Split by Word Separator " / " first
            inputText.trim().split(" / ").joinToString(" ") { word ->
                // Then split by Letter Separator " "
                word.split(" ").map { code ->
                    MorseData.codeToLetter[code] ?: '?'
                }.joinToString("")
            }
        } else {
            // Translate Text -> Morse
            inputText.uppercase().map { char ->
                if (char == ' ') " / " else MorseData.letterToCode[char] ?: "?"
            }.joinToString(" ")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Translate & Transmit") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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

            // --- Top Section: Display Areas ---

            // 1. Source Box (Input)
            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    if (!isMorseInput) {
                        if (it.length <= 20) inputText = it
                    }
                },
                readOnly = isMorseInput,
                label = { Text(if (isMorseInput) "Morse Input" else "Natural Language Input") },
                placeholder = {
                    Text(if (isMorseInput) "Tap buttons below..." else "Type text here...")
                },
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                // Highlight the active character ONLY if we are in Morse Mode (since Input is the Morse source)
                visualTransformation = if (isMorseInput && activeCharIndex != -1) {
                    HighlightTransformation(activeCharIndex)
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp), // Slightly taller for larger font
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            // Swap Button
            IconButton(
                onClick = {
                    isMorseInput = !isMorseInput
                    inputText = ""
                    activeCharIndex = -1
                    focusManager.clearFocus()
                },
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Swap Modes",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // 2. Target Box (Translation Result)
            // If in Text Mode, the Translation is Morse, so we highlight it there.
            DisplayCard(
                label = if (isMorseInput) "Natural Language Translation" else "Morse Translation",
                text = translatedText,
                color = MaterialTheme.colorScheme.secondaryContainer,
                highlightIndex = if (!isMorseInput) activeCharIndex else -1
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Middle Section: Action & Options ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Display/Transmit Button
                Button(
                    onClick = {
                        if (!isTransmitting) {
                            transmissionJob = scope.launch {
                                isTransmitting = true
                                try {
                                    val morseToTransmit = if (isMorseInput) inputText else translatedText
                                    transmitMorse(
                                        context,
                                        morseToTransmit,
                                        selectedMethod
                                    ) { index ->
                                        activeCharIndex = index // Update UI highlight
                                    }
                                } finally {
                                    isTransmitting = false
                                    activeCharIndex = -1 // Reset highlight
                                }
                            }
                        }
                    },
                    enabled = !isTransmitting && (inputText.isNotEmpty() || translatedText.isNotEmpty()),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("Display / Transmit")
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Dropdown for Method
                Box(modifier = Modifier.weight(0.6f)) {
                    OutlinedButton(
                        onClick = { isDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(selectedMethod)
                    }
                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Vibrate") },
                            onClick = { selectedMethod = "Vibrate"; isDropdownExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Flash") },
                            onClick = { selectedMethod = "Flash"; isDropdownExpanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // --- Bottom Section: Input Area ---
            if (isTransmitting || isMorseInput) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(Color.Transparent, RoundedCornerShape(8.dp))
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isTransmitting) {
                        // STOP BUTTON
                        Button(
                            onClick = {
                                transmissionJob?.cancel()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "STOP TRANSMISSION",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // MORSE INPUT BUTTONS
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Row 1: Dit and Dah
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Button(
                                    onClick = { if (inputText.length < 50) inputText += "." },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Dit\n(•)", fontSize = 24.sp, textAlign = TextAlign.Center)
                                }

                                Button(
                                    onClick = { if (inputText.length < 50) inputText += "-" },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Dah\n(—)", fontSize = 24.sp, textAlign = TextAlign.Center)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Row 2: Letter Space, Word Space, Backspace
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(70.dp), // Taller for easier tapping
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Letter Space (Next Letter)
                                Button(
                                    onClick = { if (inputText.length < 50) inputText += " " },
                                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Letter\nSpace", fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
                                }

                                // Word Space (New Button)
                                Button(
                                    onClick = { if (inputText.length < 50) inputText += " / " },
                                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Word\nSpace", fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
                                }

                                // Backspace
                                Button(
                                    onClick = {
                                        if (inputText.isNotEmpty()) {
                                            // Smart Backspace: Remove whole separators if present
                                            inputText = if (inputText.endsWith(" / ")) {
                                                inputText.dropLast(3)
                                            } else {
                                                inputText.dropLast(1)
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(0.8f).fillMaxHeight(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("⌫", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DisplayCard(label: String, text: String, color: Color, highlightIndex: Int = -1) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))

            // Highlight Logic using AnnotatedString
            val annotatedText = buildAnnotatedString {
                val safeText = text.ifEmpty { "Waiting for input..." }
                append(safeText)

                // If we have a valid index to highlight
                if (highlightIndex >= 0 && highlightIndex < safeText.length) {
                    addStyle(
                        style = SpanStyle(
                            color = Color.Red,
                            fontWeight = FontWeight.ExtraBold,
                            background = Color.Yellow.copy(alpha = 0.3f)
                        ),
                        start = highlightIndex,
                        end = highlightIndex + 1
                    )
                }
            }

            Text(
                text = annotatedText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                maxLines = 3
            )
        }
    }
}

// Custom transformation to highlight text inside the Input Field
class HighlightTransformation(private val activeIndex: Int) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            buildAnnotatedString {
                append(text.text)
                if (activeIndex in text.text.indices) {
                    addStyle(
                        SpanStyle(
                            color = Color.Red,
                            fontWeight = FontWeight.ExtraBold,
                            background = Color.Yellow.copy(alpha = 0.3f)
                        ),
                        activeIndex,
                        activeIndex + 1
                    )
                }
            },
            OffsetMapping.Identity
        )
    }
}

// --- Transmission Logic ---

suspend fun transmitMorse(
    context: Context,
    morseCode: String,
    method: String,
    onIndexUpdate: (Int) -> Unit
) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = try { cameraManager.cameraIdList[0] } catch (e: Exception) { null }

    val DIT = 200L
    val DAH = 600L
    val SYMBOL_SPACE = 200L
    val LETTER_SPACE = 600L

    // We iterate with index so we can report back to the UI which char is playing
    for ((index, char) in morseCode.withIndex()) {
        onIndexUpdate(index) // Highlight this char

        when (char) {
            '.' -> {
                performSignal(vibrator, cameraManager, cameraId, method, DIT)
                delay(SYMBOL_SPACE)
            }
            '-' -> {
                performSignal(vibrator, cameraManager, cameraId, method, DAH)
                delay(SYMBOL_SPACE)
            }
            ' ' -> {
                delay(LETTER_SPACE)
            }
            '/' -> {
                delay(LETTER_SPACE * 2) // Longer pause for word separators
            }
        }
    }
}

suspend fun performSignal(
    vibrator: Vibrator,
    cameraManager: CameraManager,
    cameraId: String?,
    method: String,
    duration: Long
) {
    if (method == "Vibrate") {
        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        delay(duration)
    } else if (method == "Flash" && cameraId != null) {
        try {
            cameraManager.setTorchMode(cameraId, true)
            delay(duration)
            cameraManager.setTorchMode(cameraId, false)
        } catch (e: Exception) {
            e.printStackTrace()
            delay(duration)
        }
    } else {
        delay(duration)
    }
}