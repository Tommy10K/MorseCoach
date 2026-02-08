MorseCoach Project Specification (Current State)

1. Project Overview

Name: MorseCoach
Platform: Android (Min SDK 26, Target SDK 36)
Language: Kotlin
UI Framework: Jetpack Compose (Material3)
Architecture: Single Activity (MainActivity) + Navigation Compose (NavHost).
State/Logic: MVVM-style ViewModels + repositories + Compose state.
Backend: Firebase (Authentication, Firestore).

2. Core Purpose

Educational app for learning and practicing Morse code with gamified lessons, realistic input modes, and utility tools to translate and transmit Morse via device hardware (audio, vibration, flashlight).

3. App Navigation (Routes)

MainActivity hosts a single NavHost. Current routes:
- home
- login
- learn_menu
- lesson_list
- lesson/{lessonId}
- glossary
- practice (Practice Menu)
- practice_standard
- practice_quiz/{char}/{isRandom}
- practice_reverse
- practice_listening
- challenges_menu
- challenge (Racer mode)
- keyer_challenge (Keyer mode)
- leaderboard
- translate
- profile

4. Tech Stack & Libraries

Build System: Gradle (Kotlin DSL)
Navigation: androidx.navigation:navigation-compose
Async: Kotlin Coroutines (suspend, viewModelScope, rememberCoroutineScope)
Firebase:
- firebase-auth (email/password)
- firebase-firestore (progress, stats, leaderboards, lessons)
Hardware:
- VibratorManager / Vibrator (haptic)
- CameraManager (flashlight)
- ToneGenerator (audio beeps)
Optional AI: Gemini API for generating Racer challenge phrases (BuildConfig.GEMINI_API_KEY).

5. UI/UX Design System

Theme: "Modern Military"
Primary: Military Green (#5A6640)
Background: Dark (#1B1C18)
Text: Off-White (#F0F0F0)
Components: large touch targets, Dit/Dah buttons, display cards, progress bars, animated feedback.

6. Feature Set (Current)

6.1 Authentication (AuthRepository.kt, LoginScreen.kt)
- Email/Password login
- Registration with auto profile creation
- Auth state listener in MainActivity

6.2 Home & Menus
- Home screen with entry points: Learn, Translate, Challenges, Profile, Login
- Learn menu: Lessons, Practice, Glossary
- Challenges menu: Racer Mode, Keyer Mode, Leaderboard
- Practice menu: Standard, Reverse, Listening

6.3 Lessons (LessonListScreen.kt, LessonScreen.kt, LessonRepository.kt, LessonViewModel.kt)
- Lesson list with lock/unlock based on previous completion
- Lesson flow with Teach and Quiz steps for each character
- Teach step plays BOTH audio + vibration simultaneously
- Quiz step uses Dit/Dah buttons with correctness checks
- Lessons are seeded into Firestore on first load if missing
- Completion stored in users.completedLessons

6.4 Practice (PracticeMenuScreen.kt, PracticeScreen.kt, PracticeQuizScreen.kt)
- Standard Practice: select character or random practice
- Quiz UI with hidden answer and hint button
- Streak system for random practice only (no hint + correct)
- Hint breaks streak and sets streak to 0
- Try again message clears input
- High streak persisted in users.practiceHighStreak

6.5 Reverse Practice (ReversePracticeScreen.kt)
- Shows Morse code, user selects correct letter from grid
- Hint reveals answer and breaks streak
- Streak resets on wrong answer
- High streak persisted in users.reverseHighStreak

6.6 Listening Practice (ListeningPracticeScreen.kt)
- 3-phase flow: Listen -> Type Morse -> Guess Letter
- Audio playback with replay button (user-controlled)
- Hint reveals Morse answer and breaks streak
- Streak increments only if Morse + letter correct without hint
- High streak persisted in users.listeningHighStreak

6.7 Translate & Transmit (TranslateScreen.kt)
- Bi-directional Text <-> Morse conversion
- Morse input UI with Dit/Dah, letter/word space, smart backspace
- Transmission via Vibrate / Flash / Audio
- Visual highlighting of current symbol during transmission
- Stop button for active transmission

6.8 Racer Mode (ChallengeScreen.kt, ChallengeViewModel.kt, ChallengeRepository.kt)
- Generates phrases (Gemini or fallback list)
- Measures WPM and accuracy based on input attempts
- Submits runs to leaderboards
- Stores lifetime stats and run history

6.9 Keyer Mode (KeyerChallengeScreen.kt)
- Realistic single-key input using press duration thresholds
- Difficulty modes: Relaxed / Normal / Fast
- Phrase list shared with Racer mode
- Audio feedback while key is pressed
- Letter/word space detection with animated indicators
- Completion increments counters in:
  - users.keyerRelaxedCompletions
  - users.keyerNormalCompletions
  - users.keyerFastCompletions

6.10 Leaderboards (LeaderboardScreen.kt)
- Top 10 runs (by WPM)
- Global PBs (per-user personal bests)
- Highlights current user

6.11 Profile & Stats (ProfileScreen.kt)
- Edit username and password
- Username syncs leaderboard entries
- Stats:
  - Personal best WPM
  - Lessons completed
  - Last 10 runs average WPM and accuracy
  - Lifetime average WPM and accuracy + total runs
  - Keyer completions (Relaxed/Normal/Fast)
  - Practice high streaks (Standard/Reverse/Listening)

7. Data Structures

7.1 Local Data (MorseData.kt)
- letterToCode: Map<Char, String>
- codeToLetter: Map<String, Char>

7.2 Firestore Schema (Current)

Collection: users (docId = Firebase Auth UID)
- email: String
- username: String
- currentLevelIndex: Int (legacy, not actively used)
- completedLessons: List<String>
- highScore: Double (PB WPM)
- lifetimeRuns: Long
- lifetimeWpmSum: Double
- lifetimeAccuracySum: Double
- keyerRelaxedCompletions: Int
- keyerNormalCompletions: Int
- keyerFastCompletions: Int
- practiceHighStreak: Int
- reverseHighStreak: Int
- listeningHighStreak: Int

Subcollection: users/{uid}/run_history
- wpm: Double
- accuracy: Double
- timestamp: Timestamp

Collection: leaderboard_runs
- userId: String
- username: String
- wpm: Double
- accuracy: Double
- timestamp: Timestamp

Collection: leaderboard_pbs (docId = userId)
- userId: String
- username: String
- wpm: Double
- accuracy: Double
- timestamp: Timestamp

Collection: lessons
- id: String
- title: String
- order: Int
- content: String (space-separated characters)
- type: String ("learn" or "quiz")

8. Security & Permissions

Android Manifest:
- android.permission.VIBRATE
- android.permission.CAMERA
- uses-feature camera (not required)

Git:
- google-services.json is ignored

9. Known Constraints / Design Notes

- Translate screen limits text input length to 20 characters in Text mode.
- Practice streaks only increment without hints; hints reset streaks.
- Keyer mode uses timing thresholds per difficulty for dit/dah detection.
- Phrase generation uses Gemini if API key exists, otherwise fallback list.

10. Next Steps / Ideas (Optional)

- Persist practice mastery per character (practiceProgress currently in-memory).
- Add analytics or session summaries for practice modes.
- Expand lesson content beyond seed set.
- Add spaced repetition or adaptive difficulty for practice.
