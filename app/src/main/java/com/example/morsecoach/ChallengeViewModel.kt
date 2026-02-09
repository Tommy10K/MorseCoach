package com.example.morsecoach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChallengeViewModel : ViewModel() {
    private val repository = ChallengeRepository()
    private val authRepo = AuthRepository()

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

    // Per-character error tracking for charStats
    // Maps each letter in the phrase to whether it had an error
    private val charErrors = mutableSetOf<Int>()  // indices into targetPhrase with errors
    private var charStartTimes = mutableListOf<Long>() // start time per morse-letter segment
    private var currentCharIndex = 0  // which letter we're currently on in the morse

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
            charErrors.clear()
            currentCharIndex = 0
            // Build per-letter start times array
            charStartTimes = MutableList(phrase.replace(" ", "").length) { 0L }
            if (charStartTimes.isNotEmpty()) charStartTimes[0] = System.currentTimeMillis()
            _isLoading.value = false
        }
    }

    fun onInputChange(input: String) {
        val previousInput = _currentInput.value
        _currentInput.value = input

        val isAppend = input.length > previousInput.length && input.startsWith(previousInput)
        if (isAppend) {
            totalInputAttempts += 1
        }
        
        if (!_targetMorse.value.startsWith(input)) {
            _isError.value = true
            if (isAppend) {
                incorrectAttempts += 1
                // Mark current letter index as having an error
                charErrors.add(currentCharIndex)
            }
        } else {
            _isError.value = false
            
            // Track which letter we've moved to by counting complete letter separators
            val lettersCompleted = countCompletedLetters(input)
            if (lettersCompleted > currentCharIndex && lettersCompleted < charStartTimes.size) {
                currentCharIndex = lettersCompleted
                charStartTimes[currentCharIndex] = System.currentTimeMillis()
            }
            
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
                
                // Record per-character stats
                val phrase = _targetPhrase.value.uppercase().replace(" ", "")
                val now = System.currentTimeMillis()
                for (i in phrase.indices) {
                    val ch = phrase[i]
                    if (!ch.isLetter()) continue
                    val hadError = charErrors.contains(i)
                    val charTime = if (i + 1 < charStartTimes.size) {
                        charStartTimes[i + 1] - charStartTimes[i]
                    } else {
                        now - charStartTimes.getOrElse(i) { startTime }
                    }
                    authRepo.recordCharAttempt(ch, !hadError, charTime.coerceAtLeast(0))
                }
            }
        }
    }

    private fun convertToMorse(text: String): String {
        return text.trim().uppercase().split(" ").joinToString(" / ") { word ->
            word.map { char -> MorseData.letterToCode[char] ?: "" }.joinToString(" ")
        }
    }

    /**
     * Count how many letter-separators (" ") have been completed in the input.
     * This tells us which letter index the user is currently typing.
     */
    private fun countCompletedLetters(input: String): Int {
        if (input.isEmpty()) return 0
        var count = 0
        var i = 0
        while (i < input.length) {
            if (input.startsWith(" / ", i)) {
                count++
                i += 3
            } else if (input[i] == ' ') {
                count++
                i++
            } else {
                i++
            }
        }
        return count
    }
}
