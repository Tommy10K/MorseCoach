package com.example.morsecoach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogoutClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val authRepo = remember { AuthRepository() }
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var pbWpm by remember { mutableStateOf<Double?>(null) }
    var lessonsCompletedCount by remember { mutableStateOf(0) }

    var last20AvgWpm by remember { mutableStateOf<Double?>(null) }
    var last20AvgAccuracy by remember { mutableStateOf<Double?>(null) }
    var lifetimeAvgWpm by remember { mutableStateOf<Double?>(null) }
    var lifetimeAvgAccuracy by remember { mutableStateOf<Double?>(null) }
    var lifetimeRuns by remember { mutableStateOf<Long?>(null) }

    // Load initial data
    LaunchedEffect(uid) {
        if (uid == null) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                val data = userDoc.data

                if (data != null) {
                    // If username field exists, use it. Otherwise fallback to email.
                    username = (data["username"] as? String) ?: (data["email"] as? String) ?: ""

                    pbWpm = (data["highScore"] as? Number)?.toDouble()
                    val completedLessons = data["completedLessons"] as? List<*>
                    lessonsCompletedCount = completedLessons?.size ?: 0

                    val runs = (data["lifetimeRuns"] as? Number)?.toLong() ?: 0L
                    val wpmSum = (data["lifetimeWpmSum"] as? Number)?.toDouble() ?: 0.0
                    val accSum = (data["lifetimeAccuracySum"] as? Number)?.toDouble() ?: 0.0

                    lifetimeRuns = runs
                    if (runs > 0) {
                        lifetimeAvgWpm = kotlin.math.round((wpmSum / runs.toDouble()) * 10) / 10.0
                        lifetimeAvgAccuracy = kotlin.math.round((accSum / runs.toDouble()) * 10) / 10.0
                    } else {
                        lifetimeAvgWpm = null
                        lifetimeAvgAccuracy = null
                    }
                }

                val historySnap = db.collection("users").document(uid)
                    .collection("run_history")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(20)
                    .get()
                    .await()

                val history = historySnap.documents.mapNotNull { doc ->
                    val wpm = (doc.get("wpm") as? Number)?.toDouble()
                    val acc = (doc.get("accuracy") as? Number)?.toDouble()
                    if (wpm != null && acc != null) wpm to acc else null
                }

                if (history.isNotEmpty()) {
                    val avgWpm = history.map { it.first }.average()
                    val avgAcc = history.map { it.second }.average()
                    last20AvgWpm = kotlin.math.round(avgWpm * 10) / 10.0
                    last20AvgAccuracy = kotlin.math.round(avgAcc * 10) / 10.0
                } else {
                    last20AvgWpm = null
                    last20AvgAccuracy = null
                }
            } catch (_: Exception) {
                // Keep stats as null/0 if fetch fails.
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Stats
            Text(
                text = "Your Stats",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            val pbText = pbWpm
                ?.takeIf { it > 0.0 }
                ?.let { String.format(Locale.US, "%.1f", it) }
                ?: "—"

            val last20WpmText = last20AvgWpm
                ?.let { String.format(Locale.US, "%.1f", it) }
                ?: "—"
            val last20AccText = last20AvgAccuracy
                ?.let { String.format(Locale.US, "%.1f", it) }
                ?: "—"

            val lifetimeWpmText = lifetimeAvgWpm
                ?.let { String.format(Locale.US, "%.1f", it) }
                ?: "—"
            val lifetimeAccText = lifetimeAvgAccuracy
                ?.let { String.format(Locale.US, "%.1f", it) }
                ?: "—"

            Text(
                text = "Personal Best: $pbText WPM",
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Lessons Completed: $lessonsCompletedCount",
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Last 20 Avg: $last20WpmText WPM • $last20AccText% accuracy",
                modifier = Modifier.fillMaxWidth()
            )

            val runsText = lifetimeRuns?.toString() ?: "—"
            Text(
                text = "Lifetime Avg: $lifetimeWpmText WPM • $lifetimeAccText% accuracy ($runsText runs)",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Message Display
            if (message != null) {
                Text(
                    text = message!!,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Username Field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("New Password (leave empty to keep)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Update Button
            Button(
                onClick = {
                    isLoading = true
                    message = "Updating..."

                    // Logic to update fields
                    if (username.isNotBlank()) {
                        authRepo.updateUsername(username) { success, err ->
                            if (!success) message = "Username update failed: $err"
                        }
                    }

                    if (password.isNotBlank()) {
                        authRepo.updatePassword(password) { success, err ->
                            if (success) {
                                message = "Profile updated successfully"
                                password = "" // Clear password field
                            } else {
                                message = "Password update failed: $err"
                            }
                            isLoading = false
                        }
                    } else {
                        // Just username update or nothing
                        message = "Profile updated successfully"
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading
            ) {
                Text("Save Changes")
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Log Out Button
            Button(
                onClick = onLogoutClick,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Log Out", fontWeight = FontWeight.Bold)
            }
        }
    }
}