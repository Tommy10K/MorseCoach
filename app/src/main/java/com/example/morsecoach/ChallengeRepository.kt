package com.example.morsecoach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ChallengeRepository {

    private val apiKey = BuildConfig.GEMINI_API_KEY

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
}