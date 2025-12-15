MorseCoach Project Specification

1. Project Overview

Name: MorseCoach
Platform: Android (Min SDK 26, Target SDK 36)
Language: Kotlin
UI Framework: Jetpack Compose (Material3)
Architecture: Single Activity (MainActivity), Navigation Component (NavHost).
Backend: Firebase (Authentication, Firestore).

2. Core Purpose

An educational Android application designed to teach Morse code via "Duolingo-style" gamified lessons, while providing utility tools for translating text-to-morse and transmitting it via device hardware (vibration/flashlight).

3. Tech Stack & Libraries

Build System: Gradle (Kotlin DSL).

Navigation: androidx.navigation:navigation-compose.

Async: Kotlin Coroutines (suspend functions, viewModelScope equivalent via rememberCoroutineScope).

Firebase:

firebase-auth: Email/Password authentication.

firebase-firestore: User progress and lesson content storage.

Hardware Access:

VibratorManager / Vibrator (Haptic feedback).

CameraManager (Flashlight/Torch).

4. UI/UX Design System

Theme: "Modern Military".

Colors:

Primary: Military Green (#5A6640)

Background: Dark (#1B1C18)

Text: Off-White (#F0F0F0)

Error: Red (Standard Material Error)

Components: Large touch targets, custom DisplayCard for text output, specific "Dit" (Dot) and "Dah" (Dash) buttons.

5. Data Structures

5.1 Local Data (MorseData.kt)

letterToCode: Map<Char, String> (A -> ".-")

codeToLetter: Map<String, Char> ( ".-" -> A)

5.2 Firestore Schema

Collection: users

docId: Firebase Auth UID

email: String

username: String

currentLevelIndex: Integer

completedLessons: List<String> (IDs of completed lessons)

highScore: Integer

Collection: lessons (To Be Implemented)

docId: String (e.g., "lesson_01")

title: String (e.g., "Basic Vowels")

order: Integer (1, 2, 3...)

content: String (The characters/words to teach, e.g., "E T A")

type: String ("learn" or "quiz")

6. Current Feature Implementation Status

6.1 Authentication (AuthRepository.kt, LoginScreen.kt)

[x] Email/Password Login.

[x] Registration with auto-profile creation in Firestore.

[x] Auth state listener in MainActivity.

6.2 Profile (ProfileScreen.kt)

[x] Edit Username.

[x] Update Password.

[x] Logout functionality.

6.3 Translation & Transmission (TranslateScreen.kt)

[x] Bi-directional Translation: Real-time conversion between Text and Morse.

[x] Inputs:

Text Mode: Standard keyboard.

Morse Mode: Custom UI with Dit, Dah, Letter Space, Word Space, Backspace.

[x] Hardware Transmission:

Vibrate (using VibrationEffect).

Flash (using CameraManager.setTorchMode).

[x] Visual Feedback: Highlighting of active character during transmission.

[x] Concurrency: Coroutine-based transmission with "Stop" cancellation support.

6.4 Learning (HomeScreen.kt, LearnMenuScreen.kt, GlossaryScreen.kt)

[x] Glossary: Grid view of all characters; clicking shows Morse code in dialog.

[x] Menu: Selection between Glossary and Lessons.

[ ] Lessons: Not yet implemented. Logic needs to fetch lessons collection and track progress in users collection.

7. Security & Permissions

Manifest:

android.permission.VIBRATE

android.permission.CAMERA

<uses-feature android:name="android.hardware.camera" android:required="false" />

Git: google-services.json is ignored.

8. Next Steps for AI Agent

Implement LessonRepository to fetch data from Firestore.

Create LessonScreen UI to handle teaching logic (displaying characters, verifying user input).

Implement progress tracking (updating currentLevelIndex in Firestore).