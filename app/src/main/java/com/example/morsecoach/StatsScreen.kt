package com.example.morsecoach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.morsecoach.ui.theme.MilitaryGreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBackClick: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid }

    // Racer stats
    var pbWpm by remember { mutableStateOf<Double?>(null) }
    var last10AvgWpm by remember { mutableStateOf<Double?>(null) }
    var last10AvgAccuracy by remember { mutableStateOf<Double?>(null) }
    var lifetimeAvgWpm by remember { mutableStateOf<Double?>(null) }
    var lifetimeAvgAccuracy by remember { mutableStateOf<Double?>(null) }
    var lifetimeRuns by remember { mutableStateOf<Long?>(null) }

    // Keyer stats
    var keyerRelaxed by remember { mutableStateOf(0) }
    var keyerNormal by remember { mutableStateOf(0) }
    var keyerFast by remember { mutableStateOf(0) }

    // Practice stats
    var lessonsCompletedCount by remember { mutableStateOf(0) }
    var practiceStreak by remember { mutableStateOf(0) }
    var reverseStreak by remember { mutableStateOf(0) }
    var listeningStreak by remember { mutableStateOf(0) }

    LaunchedEffect(uid) {
        if (uid == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                val data = userDoc.data ?: return@withContext

                pbWpm = (data["highScore"] as? Number)?.toDouble()
                lessonsCompletedCount = (data["completedLessons"] as? List<*>)?.size ?: 0

                val runs = (data["lifetimeRuns"] as? Number)?.toLong() ?: 0L
                val wpmSum = (data["lifetimeWpmSum"] as? Number)?.toDouble() ?: 0.0
                val accSum = (data["lifetimeAccuracySum"] as? Number)?.toDouble() ?: 0.0
                lifetimeRuns = runs
                if (runs > 0) {
                    lifetimeAvgWpm = kotlin.math.round((wpmSum / runs) * 10) / 10.0
                    lifetimeAvgAccuracy = kotlin.math.round((accSum / runs) * 10) / 10.0
                }

                keyerRelaxed = (data["keyerRelaxedCompletions"] as? Number)?.toInt() ?: 0
                keyerNormal = (data["keyerNormalCompletions"] as? Number)?.toInt() ?: 0
                keyerFast = (data["keyerFastCompletions"] as? Number)?.toInt() ?: 0

                practiceStreak = (data["practiceHighStreak"] as? Number)?.toInt() ?: 0
                reverseStreak = (data["reverseHighStreak"] as? Number)?.toInt() ?: 0
                listeningStreak = (data["listeningHighStreak"] as? Number)?.toInt() ?: 0

                // Last 10 run history
                val historySnap = db.collection("users").document(uid)
                    .collection("run_history")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(10)
                    .get()
                    .await()

                val history = historySnap.documents.mapNotNull { doc ->
                    val wpm = (doc.get("wpm") as? Number)?.toDouble()
                    val acc = (doc.get("accuracy") as? Number)?.toDouble()
                    if (wpm != null && acc != null) wpm to acc else null
                }
                if (history.isNotEmpty()) {
                    last10AvgWpm = kotlin.math.round(history.map { it.first }.average() * 10) / 10.0
                    last10AvgAccuracy = kotlin.math.round(history.map { it.second }.average() * 10) / 10.0
                }
            } catch (_: Exception) { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personal Stats", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // ── Racer Section ─────────────────────────────────────────
            StatsSectionHeader(title = "⚡  Racer")

            StatsCard {
                StatRow("Personal Best", formatWpm(pbWpm))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                StatRow("Last 10 Avg WPM", formatWpm(last10AvgWpm))
                StatRow("Last 10 Avg Accuracy", formatPct(last10AvgAccuracy))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                StatRow("Lifetime Avg WPM", formatWpm(lifetimeAvgWpm))
                StatRow("Lifetime Avg Accuracy", formatPct(lifetimeAvgAccuracy))
                StatRow("Total Runs", lifetimeRuns?.toString() ?: "—")
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Keyer Section ─────────────────────────────────────────
            StatsSectionHeader(title = "🎵  Keyer")

            StatsCard {
                StatRow("Relaxed Completions", keyerRelaxed.toString())
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                StatRow("Normal Completions", keyerNormal.toString())
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                StatRow("Fast Completions", keyerFast.toString())
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Practice Section ──────────────────────────────────────
            StatsSectionHeader(title = "📚  Practice")

            StatsCard {
                StatRow("Lessons Completed", lessonsCompletedCount.toString())
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Text(
                    "High Streaks",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
                StatRow("Standard", practiceStreak.toString())
                StatRow("Reverse", reverseStreak.toString())
                StatRow("Listening", listeningStreak.toString())
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Helper composables ──────────────────────────────────────────────────

@Composable
private fun StatsSectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MilitaryGreen)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun StatsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

private fun formatWpm(v: Double?): String =
    v?.takeIf { it > 0.0 }?.let { String.format(Locale.US, "%.1f WPM", it) } ?: "—"

private fun formatPct(v: Double?): String =
    v?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—"
