package com.example.morsecoach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LessonViewModel : ViewModel() {
    private val repository = LessonRepository()
    private val auth = FirebaseAuth.getInstance()

    private val _lessons = MutableStateFlow<List<Lesson>>(emptyList())
    val lessons: StateFlow<List<Lesson>> = _lessons.asStateFlow()

    private val _completedLessonIds = MutableStateFlow<List<String>>(emptyList())
    val completedLessonIds: StateFlow<List<String>> = _completedLessonIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        fetchLessons()
        fetchUserProgress()
    }

    fun fetchLessons() {
        viewModelScope.launch {
            _isLoading.value = true
            _lessons.value = repository.getLessons()
            _isLoading.value = false
        }
    }

    fun fetchUserProgress() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            viewModelScope.launch {
                _completedLessonIds.value = repository.getCompletedLessons(userId)
            }
        }
    }

    fun completeLesson(lessonId: String) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            viewModelScope.launch {
                repository.markLessonComplete(userId, lessonId)
                fetchUserProgress() // Refresh progress
            }
        }
    }
}
