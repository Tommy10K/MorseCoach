package com.example.morsecoach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChallengeViewModel : ViewModel() {
    private val repository = ChallengeRepository()

    private val _targetPhrase = MutableStateFlow("")
    val targetPhrase: StateFlow<String> = _targetPhrase.asStateFlow()

    private val _targetMorse = MutableStateFlow("")
    val targetMorse: StateFlow<String> = _targetMorse.asStateFlow()

    private val _currentInput = MutableStateFlow("")
    val currentInput: StateFlow<String> = _currentInput.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()

    private val _wpm = MutableStateFlow<Double?>(null)
    val wpm: StateFlow<Double?> = _wpm.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var startTime: Long = 0
    private var hasSubmittedScore: Boolean = false

    private var totalInputAttempts: Int = 0
    private var incorrectAttempts: Int = 0

    fun startNewGame() {
        viewModelScope.launch {
            _isLoading.value = true
            _wpm.value = null
            _currentInput.value = ""
            _isError.value = false
            hasSubmittedScore = false
            totalInputAttempts = 0
            incorrectAttempts = 0
            
            val phrase = repository.generateChallengePhrase()
            _targetPhrase.value = phrase
            _targetMorse.value = convertToMorse(phrase)
            
            startTime = System.currentTimeMillis()
            _isLoading.value = false
        }
    }

    fun onInputChange(input: String) {
        val previousInput = _currentInput.value
        _currentInput.value = input

        // Count only "append" actions as attempts (dit/dah/space buttons).
        // Backspaces reduce the string and shouldn't penalize accuracy.
        val isAppend = input.length > previousInput.length && input.startsWith(previousInput)
        if (isAppend) {
            totalInputAttempts += 1
        }
        
        // Check for error immediately (prefix match)
        if (!_targetMorse.value.startsWith(input)) {
            _isError.value = true
            if (isAppend) {
                incorrectAttempts += 1
            }
        } else {
            _isError.value = false
            // Check for completion
            if (input == _targetMorse.value) {
                finishGame()
            }
        }
    }

    private fun finishGame() {
        val endTime = System.currentTimeMillis()
        val durationSeconds = (endTime - startTime) / 1000.0
        val safeDurationSeconds = durationSeconds.coerceAtLeast(1.0)
        val durationMinutes = safeDurationSeconds / 60.0
        
        // Standard WPM: (Characters / 5) / Minutes
        // We use the length of the original text phrase, not the morse code length
        val charCount = _targetPhrase.value.length
        val calculatedWpm = (charCount / 5.0) / durationMinutes
        
        val finalWpm = kotlin.math.round(calculatedWpm * 10) / 10.0
        _wpm.value = finalWpm

        val accuracy = if (totalInputAttempts <= 0) {
            100.0
        } else {
            val correct = (totalInputAttempts - incorrectAttempts).coerceAtLeast(0)
            kotlin.math.round((correct.toDouble() / totalInputAttempts.toDouble()) * 1000) / 10.0
        }

        if (!hasSubmittedScore) {
            hasSubmittedScore = true
            viewModelScope.launch {
                repository.submitScore(finalWpm, accuracy)
            }
        }
    }

    private fun convertToMorse(text: String): String {
        return text.trim().uppercase().split(" ").joinToString(" / ") { word ->
            word.map { char -> MorseData.letterToCode[char] ?: "" }.joinToString(" ")
        }
    }
}
