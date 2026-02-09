package com.example.morsecoach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.morsecoach.ui.theme.MilitaryGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Daily Challenge: one word per day, same for all users.
 * Type it in Morse, press Send. Correct → streak +1, wrong → streak = 0.
 * One attempt per day, no hints, no corrections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyChallengeScreen(onBackClick: () -> Unit) {
    val authRepo = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    val todayStr = remember { LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) }

    // Daily word pool (deterministic by day-of-year)
    val dailyWords = remember {
        listOf(
            "ALPHA", "BRAVO", "CHARLIE", "DELTA", "ECHO",
            "FOXTROT", "GOLF", "HOTEL", "INDIA", "JULIET",
            "KILO", "LIMA", "MIKE", "NOVEMBER", "OSCAR",
            "PAPA", "QUEBEC", "ROMEO", "SIERRA", "TANGO",
            "UNIFORM", "VICTOR", "WHISKEY", "XRAY", "YANKEE",
            "ZULU", "HELLO", "WORLD", "MORSE", "CODE",
            "SIGNAL", "BEACON", "ROGER", "COPY", "OVER",
            "MAYDAY", "RESCUE", "TOWER", "PILOT", "RADIO",
            "STATION", "WAVE", "PULSE", "SPARK", "DECODE",
            "CIPHER", "RELAY", "PATROL", "HARBOR", "VOYAGE",
            "ANCHOR", "STORM", "HORIZON", "COMPASS", "NIGHT",
            "DAWN", "ALERT", "DANGER", "CAPTAIN", "MISSION",
            "TARGET", "FLEET", "GUARD", "BRIDGE", "COAST",
            "EAGLE", "FALCON", "HUNTER", "SHADOW", "THUNDER",
            "BLAZE", "FROST", "SUMMIT", "VALLEY", "RIVER",
            "OCEAN", "ISLAND", "DESERT", "FOREST", "PLAINS",
            "NORTH", "SOUTH", "EAST", "WEST", "CENTER",
            "RAPID", "STEADY", "SILENT", "STRIKE", "SHIELD",
            "ORBIT", "LAUNCH", "ROCKET", "FLIGHT", "CLOUD",
            "NEXUS", "PRISM", "QUARTZ", "VERTEX", "MATRIX",
            "CIPHER", "ENIGMA", "VORTEX", "ZENITH", "APEX",
            "OMEGA", "TITAN", "ATLAS", "HYDRA", "PHOENIX",
            "COMET", "LUNAR", "SOLAR", "GAMMA", "DELTA",
            "SIGMA", "THETA", "KAPPA", "LAMBDA", "RETURN",
            "MARCH", "BRAVE", "SWIFT", "SHARP", "STEEL",
            "AMBER", "CORAL", "IVORY", "SLATE", "ONYX",
            "JADE", "OPAL", "RUBY", "TOPAZ", "PEARL",
            "FLARE", "DRIFT", "SURGE", "CREST", "DEPTH",
            "FIELD", "SCOUT", "WATCH", "TRACE", "FORCE",
            "SCALE", "RANGE", "FRONT", "RECON", "SQUAD",
            "RALLY", "CLASH", "FORGE", "VAULT", "HAVEN",
            "RIDGE", "STONE", "FLAME", "LIGHT", "SPARK",
            "ARC", "BOLT", "CORE", "DOME", "EDGE",
            "GLOW", "HAZE", "IRON", "JET", "KNOT",
            "LORE", "MIST", "NODE", "ORE", "PIKE",
            "RIFT", "SILO", "TIDE", "URN", "VINE",
            "WREN", "AXIS", "YOKE", "ZONE"
        )
    }

    // Pick today's word deterministically
    val todayWord = remember {
        val dayOfYear = LocalDate.now().dayOfYear
        val year = LocalDate.now().year
        val index = ((dayOfYear * 31 + year * 7) % dailyWords.size + dailyWords.size) % dailyWords.size
        dailyWords[index]
    }

    val todayMorse = remember(todayWord) {
        todayWord.map { char -> MorseData.letterToCode[char] ?: "?" }.joinToString(" ")
    }

    // State
    var userInput by remember { mutableStateOf("") }
    var alreadyPlayed by remember { mutableStateOf<Boolean?>(null) } // null = loading
    var currentStreak by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<Boolean?>(null) } // null = not submitted, true/false = correct/wrong

    // Check if user already played today
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val info = authRepo.getDailyChallengeInfo()
            currentStreak = info.first
            alreadyPlayed = info.second == todayStr
        }
    }

    fun submitAnswer() {
        val correct = userInput.trim() == todayMorse
        result = correct
        val newStreak = if (correct) currentStreak + 1 else 0
        currentStreak = newStreak
        scope.launch {
            authRepo.recordDailyChallenge(todayStr, newStreak)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Challenge") },
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                // Loading
                alreadyPlayed == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // Already played today (cooldown)
                alreadyPlayed == true && result == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⏳", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Already played today!",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Come back tomorrow for a new word.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Current Streak: 🔥 $currentStreak",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MilitaryGreen
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = onBackClick) {
                                Text("Go Back")
                            }
                        }
                    }
                }

                // Just submitted - show result
                result != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (result == true) "✅" else "❌",
                                fontSize = 64.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (result == true) "Correct!" else "Wrong!",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (result == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (result == true) {
                                Text(
                                    text = "Streak: 🔥 $currentStreak",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MilitaryGreen
                                )
                            } else {
                                Text(
                                    text = "Streak lost. Try again tomorrow!",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "The word was:",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            todayWord,
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            todayMorse,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = onBackClick) {
                                Text("Done")
                            }
                        }
                    }
                }

                // Active challenge - input mode
                else -> {
                    // Word display
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Today's Word",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = todayWord,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                letterSpacing = 4.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Warning
                    Text(
                        text = "⚠️ One attempt only. No hints. No corrections.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Input display
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Your Morse:",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = userInput.ifEmpty { "Tap buttons below" },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (userInput.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 1.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Input buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Dit / Dah
                        Row(
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { userInput += "." },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MilitaryGreen)
                            ) {
                                Text("Dit\n(•)", fontSize = 22.sp, textAlign = TextAlign.Center)
                            }
                            Button(
                                onClick = { userInput += "-" },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MilitaryGreen)
                            ) {
                                Text("Dah\n(—)", fontSize = 22.sp, textAlign = TextAlign.Center)
                            }
                        }

                        // Letter Space / Word Space / Backspace
                        Row(
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { userInput += " " },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Letter\nSpace", fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 15.sp)
                            }
                            Button(
                                onClick = { userInput += " / " },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Word\nSpace", fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 15.sp)
                            }
                            Button(
                                onClick = {
                                    if (userInput.isNotEmpty()) {
                                        userInput = if (userInput.endsWith(" / ")) {
                                            userInput.dropLast(3)
                                        } else {
                                            userInput.dropLast(1)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(0.7f).fillMaxHeight(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("⌫", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Submit button
                        Button(
                            onClick = { submitAnswer() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = userInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MilitaryGreen)
                        ) {
                            Text("Submit Answer", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
