package com.example.morsecoach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import com.example.morsecoach.BuildConfig
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await


class ChallengeRepository {

    private val apiKey = BuildConfig.GEMINI_API_KEY
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val fallbackPhrases = listOf(
        "SOS TITANIC",
        "THE QUICK BROWN FOX",
        "MORSE CODE IS FUN",
        "RADIO SILENCE",
        "DIT DAH DIT",
        "HELP ME",
        "CALL FOR HELP",
        "PARIS PARIS PARIS PARIS"
    )
    
    suspend fun generateChallengePhrase(): String {
        return withContext(Dispatchers.IO) {
            // 1. Check Key immediately
            if (apiKey.isEmpty() || apiKey == "YOUR_API_KEY_HERE") {
                return@withContext fallbackPhrases.random()
            }

            try {
                // 2. Attempt API Call
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val prompt = "Generate a short sentence related to Morse code, radio communication, or distress signals. It should be between 2 and 4 words long. Return ONLY the text, uppercase, no punctuation."
                
                val jsonBody = JSONObject()
                val contentsArray = org.json.JSONArray()
                val contentObject = JSONObject()
                val partsArray = org.json.JSONArray()
                val partObject = JSONObject()
                
                partObject.put("text", prompt)
                partsArray.put(partObject)
                contentObject.put("parts", partsArray)
                contentsArray.put(contentObject)
                jsonBody.put("contents", contentsArray)

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonBody.toString())
                writer.flush()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val jsonResponse = JSONObject(response.toString())
                    val candidates = jsonResponse.getJSONArray("candidates")
                    if (candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        if (parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).getString("text").trim()
                        }
                    }
                }
                
                // If we get here, API failed (non-200 or empty JSON)
                // Log it if you want: Log.e("API", "Failed code: ${connection.responseCode}")
                return@withContext fallbackPhrases.random()

            } catch (e: Exception) {
                e.printStackTrace()
                // 3. Fallback on ANY exception (No internet, etc)
                return@withContext fallbackPhrases.random()
            }
        }
    }

    suspend fun submitScore(wpm: Double, accuracy: Double = 100.0, maxHistory: Int = 20) {
        val user = auth.currentUser ?: return
        val userId = user.uid
        val timestamp = Timestamp.now()

        val username = withContext(Dispatchers.IO) {
            try {
                val profileDoc = db.collection("users").document(userId).get().await()
                profileDoc.getString("username")
                    ?: user.email?.substringBefore("@")
                    ?: "Unknown"
            } catch (e: Exception) {
                user.email?.substringBefore("@") ?: "Unknown"
            }
        }

        val runData = hashMapOf(
            "userId" to userId,
            "username" to username,
            "wpm" to wpm,
            "accuracy" to accuracy,
            "timestamp" to timestamp
        )

        withContext(Dispatchers.IO) {
            try {
                db.collection("leaderboard_runs").add(runData).await()

                val userRef = db.collection("users").document(userId)

                // Lifetime aggregates (accurate forever, O(1) to read)
                userRef.set(
                    mapOf(
                        "lifetimeRuns" to FieldValue.increment(1),
                        "lifetimeWpmSum" to FieldValue.increment(wpm),
                        "lifetimeAccuracySum" to FieldValue.increment(accuracy)
                    ),
                    SetOptions.merge()
                ).await()

                // Per-user run history (keep newest N docs)
                val historyRef = userRef.collection("run_history")
                historyRef.add(
                    mapOf(
                        "wpm" to wpm,
                        "accuracy" to accuracy,
                        "timestamp" to timestamp
                    )
                ).await()

                // Prune older history docs beyond maxHistory
                if (maxHistory > 0) {
                    val newest = historyRef
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(maxHistory.toLong())
                        .get()
                        .await()

                    if (newest.size() >= maxHistory) {
                        val lastKept = newest.documents.lastOrNull()
                        if (lastKept != null) {
                            val older = historyRef
                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                .startAfter(lastKept)
                                .get()
                                .await()

                            if (!older.isEmpty) {
                                db.runBatch { batch ->
                                    older.documents.forEach { doc ->
                                        batch.delete(doc.reference)
                                    }
                                }.await()
                            }
                        }
                    }
                }

                db.runTransaction { transaction ->
                    val snapshot = transaction.get(userRef)
                    val currentBest = snapshot.getDouble("highScore")
                        ?: snapshot.getLong("highScore")?.toDouble()
                        ?: 0.0
                    if (wpm > currentBest) {
                        transaction.update(userRef, "highScore", wpm)
                    }
                }.await()

                val pbRef = db.collection("leaderboard_pbs").document(userId)
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(pbRef)
                    val currentBest = snapshot.getDouble("wpm") ?: 0.0
                    if (wpm > currentBest) {
                        transaction.set(pbRef, runData)
                    }
                }.await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getTopRuns(): List<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val result = db.collection("leaderboard_runs")
                    .orderBy("wpm", Query.Direction.DESCENDING)
                    .limit(10)
                    .get()
                    .await()

                result.documents.map { doc ->
                    mapOf(
                        "username" to (doc.getString("username") ?: "Unknown"),
                        "wpm" to (doc.getDouble("wpm") ?: 0.0),
                        "userId" to (doc.getString("userId") ?: "")
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun getTopPBs(): List<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val result = db.collection("leaderboard_pbs")
                    .orderBy("wpm", Query.Direction.DESCENDING)
                    .limit(20)
                    .get()
                    .await()

                result.documents.map { doc ->
                    mapOf(
                        "username" to (doc.getString("username") ?: "Unknown"),
                        "wpm" to (doc.getDouble("wpm") ?: 0.0),
                        "userId" to doc.id
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}