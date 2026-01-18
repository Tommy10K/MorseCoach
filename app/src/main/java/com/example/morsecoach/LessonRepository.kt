package com.example.morsecoach

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
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
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        Log.d("LessonRepository", "Seeding lessons to Firestore (uid=$uid)")

        val initialLessons = listOf(
            Lesson("lesson_01", "The Basics", 1, "E T I M"),
            Lesson("lesson_02", "Common Letters", 2, "A N S O H"),
            Lesson("lesson_03", "Mirrors & Opposites", 3, "R K D U G W"),
            Lesson("lesson_04", "Rhythm & Flow", 4, "B V F L P"),
            Lesson("lesson_05", "Complex Characters", 5, "Q J X Y Z C"),
            Lesson("lesson_06", "Numbers 1-5", 6, "1 2 3 4 5"),
            Lesson("lesson_07", "Numbers 6-0", 7, "6 7 8 9 0")
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
