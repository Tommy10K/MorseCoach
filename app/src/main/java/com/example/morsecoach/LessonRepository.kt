package com.example.morsecoach

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class Lesson(
    val id: String = "",
    val title: String = "",
    val order: Int = 0,
    val content: String = "", // Space separated characters e.g. "A B C"
    val type: String = "learn"
)

class LessonRepository {
    private val db = FirebaseFirestore.getInstance()
    private val lessonsCollection = db.collection("lessons")
    private val usersCollection = db.collection("users")

    suspend fun getLessons(): List<Lesson> {
        return try {
            val snapshot = lessonsCollection.orderBy("order").get().await()
            if (snapshot.isEmpty) {
                seedLessons()
                // Fetch again after seeding
                lessonsCollection.orderBy("order").get().await().toObjects(Lesson::class.java)
            } else {
                snapshot.toObjects(Lesson::class.java)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun seedLessons() {
        val initialLessons = listOf(
            Lesson("lesson_01", "Basic Vowels", 1, "A E I O U"),
            Lesson("lesson_02", "Common Consonants", 2, "T N S H R"),
            Lesson("lesson_03", "The Rest of the Vowels", 3, "Y"), // Y is sometimes a vowel :)
            Lesson("lesson_04", "SOS Letters", 4, "S O"),
            Lesson("lesson_05", "Numbers 1-5", 5, "1 2 3 4 5")
        )
        initialLessons.forEach { lesson ->
            lessonsCollection.document(lesson.id).set(lesson).await()
        }
    }

    suspend fun getCompletedLessons(userId: String): List<String> {
        return try {
            val document = usersCollection.document(userId).get().await()
            val completed = document.get("completedLessons") as? List<String>
            completed ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun markLessonComplete(userId: String, lessonId: String) {
        try {
            val userRef = usersCollection.document(userId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val currentCompleted = snapshot.get("completedLessons") as? MutableList<String> ?: mutableListOf()
                if (!currentCompleted.contains(lessonId)) {
                    currentCompleted.add(lessonId)
                    transaction.update(userRef, "completedLessons", currentCompleted)
                }
            }.await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
