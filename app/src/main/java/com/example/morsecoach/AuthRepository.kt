package com.example.morsecoach

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    fun getUserId(): String? = auth.currentUser?.uid

    fun signOut() {
        auth.signOut()
    }

    fun login(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || pass.isBlank()) {
            onResult(false, "Fields cannot be empty")
            return
        }

        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.message ?: "Login failed")
                }
            }
    }

    fun register(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || pass.isBlank()) {
            onResult(false, "Fields cannot be empty")
            return
        }

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        createUserProfile(userId, email, onResult)
                    } else {
                        onResult(false, "User created but ID missing")
                    }
                } else {
                    onResult(false, task.exception?.message ?: "Registration failed")
                }
            }
    }

    private fun createUserProfile(userId: String, email: String, onResult: (Boolean, String?) -> Unit) {
        val userMap = hashMapOf(
            "email" to email,
            "username" to email,
            "usernameLower" to email.lowercase(),
            "currentLevelIndex" to 0,
            "completedLessons" to emptyList<String>(),
            "highScore" to 0.0,
            "keyerRelaxedCompletions" to 0,
            "keyerNormalCompletions" to 0,
            "keyerFastCompletions" to 0,
            "practiceHighStreak" to 0,
            "reverseHighStreak" to 0,
            "listeningHighStreak" to 0
        )

        db.collection("users").document(userId)
            .set(userMap)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e ->
                onResult(true, "Warning: Profile creation failed: ${e.message}")
            }
    }

    fun getUserProfile(onResult: (Map<String, Any>?, String?) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    onResult(document.data, null)
                } else {
                    onResult(null, "Profile not found")
                }
            }
            .addOnFailureListener { e ->
                onResult(null, e.message)
            }
    }

    fun updateUsername(newUsername: String, onResult: (Boolean, String?) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .update(
                "username", newUsername,
                "usernameLower", newUsername.lowercase()
            )
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    fun updatePassword(newPass: String, onResult: (Boolean, String?) -> Unit) {
        val user = auth.currentUser ?: return
        user.updatePassword(newPass)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    suspend fun incrementKeyerCompletion(difficulty: String) {
        val userId = auth.currentUser?.uid ?: return
        val fieldName = when(difficulty.uppercase()) {
            "RELAXED" -> "keyerRelaxedCompletions"
            "NORMAL" -> "keyerNormalCompletions"
            "FAST" -> "keyerFastCompletions"
            else -> return
        }
        try {
            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { doc ->
                    val current = (doc.get(fieldName) as? Number)?.toInt() ?: 0
                    db.collection("users").document(userId)
                        .update(fieldName, current + 1)
                }
        } catch (_: Exception) { }
    }

    suspend fun updatePracticeHighStreak(newStreak: Int) {
        val userId = auth.currentUser?.uid ?: return
        try {
            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { doc ->
                    val currentHigh = (doc.get("practiceHighStreak") as? Number)?.toInt() ?: 0
                    if (newStreak > currentHigh) {
                        db.collection("users").document(userId)
                            .update("practiceHighStreak", newStreak)
                    }
                }
        } catch (_: Exception) { }
    }
    /**
     * Record a character attempt for targeted practice.
     * Increments attempts (and mistakes if wrong) and adds elapsed time.
     */
    suspend fun recordCharAttempt(char: Char, correct: Boolean, elapsedMs: Long) {
        val userId = auth.currentUser?.uid ?: return
        val key = char.uppercaseChar().toString()
        val ref = db.collection("users").document(userId)
        try {
            val doc = ref.get().await()
            @Suppress("UNCHECKED_CAST")
            val charStats = (doc.get("charStats") as? Map<String, Map<String, Any>>)?.toMutableMap()
                ?: mutableMapOf()

            val existing = charStats[key]
            val prevAttempts = (existing?.get("attempts") as? Number)?.toLong() ?: 0L
            val prevMistakes = (existing?.get("mistakes") as? Number)?.toLong() ?: 0L
            val prevTime = (existing?.get("totalTimeMs") as? Number)?.toLong() ?: 0L

            charStats[key] = mapOf(
                "attempts" to (prevAttempts + 1),
                "mistakes" to (prevMistakes + if (correct) 0 else 1),
                "totalTimeMs" to (prevTime + elapsedMs)
            )
            ref.update("charStats", charStats)
        } catch (_: Exception) { }
    }

    /**
     * Fetch charStats map for targeted practice selection.
     */
    suspend fun getCharStats(): Map<String, Map<String, Any>> {
        val userId = auth.currentUser?.uid ?: return emptyMap()
        return try {
            val doc = db.collection("users").document(userId).get().await()
            @Suppress("UNCHECKED_CAST")
            (doc.get("charStats") as? Map<String, Map<String, Any>>) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Get the top 3 weakest characters: highest mistake rate first, then slowest.
     */
    suspend fun getWeakestCharacters(count: Int = 3): List<Char> {
        val stats = getCharStats()
        if (stats.isEmpty()) return emptyList()

        data class CharScore(val char: Char, val mistakeRate: Double, val avgTimeMs: Double)

        val scores = stats.mapNotNull { (key, value) ->
            val attempts = (value["attempts"] as? Number)?.toLong() ?: return@mapNotNull null
            if (attempts <= 0) return@mapNotNull null
            val mistakes = (value["mistakes"] as? Number)?.toLong() ?: 0L
            val totalTime = (value["totalTimeMs"] as? Number)?.toLong() ?: 0L
            CharScore(
                char = key.first(),
                mistakeRate = mistakes.toDouble() / attempts,
                avgTimeMs = totalTime.toDouble() / attempts
            )
        }

        // Primary sort: mistake rate descending; secondary: avg time descending
        return scores
            .sortedWith(compareByDescending<CharScore> { it.mistakeRate }.thenByDescending { it.avgTimeMs })
            .take(count)
            .map { it.char }
    }
    suspend fun updateReverseHighStreak(newStreak: Int) {
        val userId = auth.currentUser?.uid ?: return
        try {
            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { doc ->
                    val currentHigh = (doc.get("reverseHighStreak") as? Number)?.toInt() ?: 0
                    if (newStreak > currentHigh) {
                        db.collection("users").document(userId)
                            .update("reverseHighStreak", newStreak)
                    }
                }
        } catch (_: Exception) { }
    }

    suspend fun updateListeningHighStreak(newStreak: Int) {
        val userId = auth.currentUser?.uid ?: return
        try {
            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { doc ->
                    val currentHigh = (doc.get("listeningHighStreak") as? Number)?.toInt() ?: 0
                    if (newStreak > currentHigh) {
                        db.collection("users").document(userId)
                            .update("listeningHighStreak", newStreak)
                    }
                }
        } catch (_: Exception) { }
    }

    /**
     * Get daily challenge info: (currentStreak, lastPlayedDate).
     */
    suspend fun getDailyChallengeInfo(): Pair<Int, String> {
        val userId = auth.currentUser?.uid ?: return Pair(0, "")
        return try {
            val doc = db.collection("users").document(userId).get().await()
            val streak = (doc.get("dailyChallengeStreak") as? Number)?.toInt() ?: 0
            val lastDate = (doc.get("lastDailyChallengeDate") as? String) ?: ""
            Pair(streak, lastDate)
        } catch (_: Exception) {
            Pair(0, "")
        }
    }

    /**
     * Record a daily challenge attempt: set streak and last played date.
     */
    suspend fun recordDailyChallenge(date: String, newStreak: Int) {
        val userId = auth.currentUser?.uid ?: return
        try {
            db.collection("users").document(userId)
                .update(
                    mapOf(
                        "dailyChallengeStreak" to newStreak,
                        "lastDailyChallengeDate" to date
                    )
                )
        } catch (_: Exception) { }
    }
}