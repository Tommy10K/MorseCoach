package com.example.morsecoach

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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
            "username" to email, // Default username is email
            "currentLevelIndex" to 0,
            "completedLessons" to emptyList<String>(),
            "highScore" to 0.0
        )

        db.collection("users").document(userId)
            .set(userMap)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e ->
                onResult(true, "Warning: Profile creation failed: ${e.message}")
            }
    }

    // --- New Methods for Profile Screen ---

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
            .update("username", newUsername)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    fun updatePassword(newPass: String, onResult: (Boolean, String?) -> Unit) {
        val user = auth.currentUser ?: return
        user.updatePassword(newPass)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }
}