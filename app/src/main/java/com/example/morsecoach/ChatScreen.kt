package com.example.morsecoach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.morsecoach.ui.theme.MilitaryGreen
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    otherUid: String,
    otherUsername: String,
    viewModel: SocialViewModel,
    onBackClick: () -> Unit
) {
    val messages by viewModel.chatMessages.collectAsState()
    val chatInput by viewModel.chatInput.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val currentUid = viewModel.currentUid ?: ""
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // Track which messages have translation showing
    var translatedIds by remember { mutableStateOf(setOf<String>()) }

    // Settings menu & delete confirmation
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Start observing messages for this chat
    LaunchedEffect(chatId) {
        viewModel.openChat(chatId)
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Show snackbar messages
    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MilitaryGreen.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = otherUsername.firstOrNull()?.uppercase() ?: "?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(otherUsername, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete Chat", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MorseInputBar(
                input = chatInput,
                onInputChange = { viewModel.updateChatInput(it) },
                onSend = { viewModel.sendMessage(otherUid) }
            )
        }
    ) { innerPadding ->

        // Delete confirmation dialog
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Chat") },
                text = { Text("Are you sure you want to delete this chat? All messages will be lost.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            viewModel.deleteChat(chatId, onDeleted = onBackClick)
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No messages yet",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Send a Morse message!",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == currentUid
                    val showTranslation = msg.id in translatedIds

                    MessageBubble(
                        text = msg.text,
                        isMine = isMine,
                        timestamp = msg.timestamp?.toDate()?.let { timeFormat.format(it) } ?: "",
                        showTranslation = showTranslation,
                        onToggleTranslation = {
                            translatedIds = if (showTranslation) {
                                translatedIds - msg.id
                            } else {
                                translatedIds + msg.id
                            }
                        }
                    )
                }
            }
        }
    }
}

// ── Message bubble ──────────────────────────────────────────────────────

@Composable
private fun MessageBubble(
    text: String,
    isMine: Boolean,
    timestamp: String,
    showTranslation: Boolean,
    onToggleTranslation: () -> Unit
) {
    val alignment = if (isMine) Alignment.End else Alignment.Start
    val bubbleColor = if (isMine) {
        MilitaryGreen.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isMine) 16.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 16.dp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier
                .widthIn(min = 60.dp, max = 300.dp)
                .clickable(onClick = onToggleTranslation)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Morse text
                Text(
                    text = text,
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )

                // Timestamp
                Text(
                    text = timestamp,
                    fontSize = 10.sp,
                    color = textColor.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp)
                )
            }
        }

        // Translation (revealed on tap)
        AnimatedVisibility(
            visible = showTranslation,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val translated = translateMorseToText(text)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .padding(top = 2.dp, start = 8.dp, end = 8.dp)
                    .widthIn(max = 300.dp)
            ) {
                Text(
                    text = "Translation: $translated",
                    fontSize = 13.sp,
                    color = MilitaryGreen,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ── Morse input bar ─────────────────────────────────────────────────────

@Composable
private fun MorseInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val charCount = input.length
    val isOverLimit = charCount > 200

    var showPreview by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Display current input
            if (input.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = input,
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp,
                            letterSpacing = 1.sp,
                            color = if (isOverLimit) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "$charCount/200",
                            fontSize = 11.sp,
                            color = if (isOverLimit) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // Preview / checker area
            if (showPreview && input.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MilitaryGreen.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Text(
                        text = translateMorseToText(input).ifEmpty { "(empty)" },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Button rows
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Dit
                Button(
                    onClick = { if (charCount < 200) onInputChange(input + ".") },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MilitaryGreen)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Dit", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(".", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Dah
                Button(
                    onClick = { if (charCount < 200) onInputChange(input + "-") },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MilitaryGreen)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Dah", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Letter space
                Button(
                    onClick = { if (charCount < 200) onInputChange(input + " ") },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MilitaryGreen.copy(alpha = 0.7f)
                    )
                ) {
                    Text("Letter\nSpace", fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
                }

                // Word space
                Button(
                    onClick = {
                        if (charCount < 198) onInputChange(input + " / ")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MilitaryGreen.copy(alpha = 0.7f)
                    )
                ) {
                    Text("Word\nSpace", fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
                }

                // Backspace
                Button(
                    onClick = {
                        if (input.isNotEmpty()) {
                            val newInput = if (input.endsWith(" / ")) {
                                input.dropLast(3)
                            } else {
                                input.dropLast(1)
                            }
                            onInputChange(newInput)
                        }
                    },
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                ) {
                    Text("⌫", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                // Preview / Checker
                IconButton(
                    onClick = { showPreview = !showPreview },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            if (showPreview) MilitaryGreen
                            else MilitaryGreen.copy(alpha = 0.5f)
                        )
                ) {
                    Text(
                        "Aa",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Send
                IconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            if (input.isNotBlank() && !isOverLimit) MilitaryGreen
                            else MilitaryGreen.copy(alpha = 0.3f)
                        ),
                    enabled = input.isNotBlank() && !isOverLimit
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ── Morse translation helper ────────────────────────────────────────────

/**
 * Translates a Morse-encoded string (dots, dashes, spaces, slashes)
 * into natural language using MorseData.codeToLetter.
 */
fun translateMorseToText(morse: String): String {
    if (morse.isBlank()) return ""
    return morse.trim().split(" / ").joinToString(" ") { word ->
        word.split(" ").map { code ->
            if (code.isBlank()) return@map ""
            MorseData.codeToLetter[code]?.toString() ?: "?"
        }.joinToString("")
    }
}
