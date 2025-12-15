package com.example.morsecoach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeScreen(
    viewModel: ChallengeViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val targetPhrase by viewModel.targetPhrase.collectAsState()
    val currentInput by viewModel.currentInput.collectAsState()
    val isError by viewModel.isError.collectAsState()
    val wpm by viewModel.wpm.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startNewGame()
    }

    if (wpm != null) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss */ },
            title = { Text("Challenge Complete!") },
            text = { 
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Your Speed:", style = MaterialTheme.typography.titleMedium)
                    Text("$wpm WPM", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.startNewGame() }) {
                    Text("Play Again")
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
                title = { Text("Morse Challenge") },
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Generating Challenge...", modifier = Modifier.padding(top = 64.dp))
                }
            } else {
                // Target Text
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Translate this:", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = targetPhrase,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // User Input Display
                OutlinedTextField(
                    value = currentInput,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Your Morse Code") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    ),
                    isError = isError,
                    colors = OutlinedTextFieldDefaults.colors(
                        errorBorderColor = MaterialTheme.colorScheme.error,
                        errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                    )
                )

                Spacer(modifier = Modifier.weight(1f))

                // Controls
                Column(
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Row 1: Dit and Dah
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { viewModel.onInputChange(currentInput + ".") },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Dit\n(•)", fontSize = 24.sp, textAlign = TextAlign.Center)
                        }

                        Button(
                            onClick = { viewModel.onInputChange(currentInput + "-") },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Dah\n(—)", fontSize = 24.sp, textAlign = TextAlign.Center)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Row 2: Spaces and Backspace
                    Row(
                        modifier = Modifier.fillMaxWidth().height(70.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.onInputChange(currentInput + " ") },
                            modifier = Modifier.weight(1.2f).fillMaxHeight(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Letter\nSpace", fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
                        }

                        Button(
                            onClick = { viewModel.onInputChange(currentInput + " / ") },
                            modifier = Modifier.weight(1.2f).fillMaxHeight(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Word\nSpace", fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
                        }

                        Button(
                            onClick = {
                                if (currentInput.isNotEmpty()) {
                                    val newInput = if (currentInput.endsWith(" / ")) {
                                        currentInput.dropLast(3)
                                    } else {
                                        currentInput.dropLast(1)
                                    }
                                    viewModel.onInputChange(newInput)
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
