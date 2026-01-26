package com.example.morsecoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.morsecoach.ui.theme.MorseCoachTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MorseCoachTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val auth = remember { FirebaseAuth.getInstance() }

    var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    NavHost(navController = navController, startDestination = "home") {

        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.popBackStack()
                }
            )
        }

        composable("home") {
            HomeScreen(
                isLoggedIn = currentUser != null,
                onLearnClick = {
                    // Navigate to the intermediate menu
                    navController.navigate("learn_menu")
                },
                onTranslateClick = {
                    navController.navigate("translate")
                },
                onChallengesClick = {
                    navController.navigate("challenges_menu")
                },
                onLoginClick = {
                    navController.navigate("login")
                },
                onProfileClick = {
                    navController.navigate("profile")
                }
            )
        }

        // New Menu Screen
        composable("learn_menu") {
            LearnMenuScreen(
                onGlossaryClick = { navController.navigate("glossary") },
                onLessonsClick = { navController.navigate("lesson_list") },
                onPracticeClick = { navController.navigate("practice") },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("practice") {
            PracticeMenuScreen(
                onStandardClick = { navController.navigate("practice_standard") },
                onReverseClick = { navController.navigate("practice_reverse") },
                onListeningClick = { navController.navigate("practice_listening") },
                onBackClick = { navController.popBackStack() }
            )
        }
        
        composable("practice_standard") {
            PracticeScreen(
                onCharacterClick = { char ->
                    navController.navigate("practice_quiz/$char/false")
                },
                onRandomClick = {
                    navController.navigate("practice_quiz/_/true")
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        
        composable("practice_reverse") {
            ReversePracticeScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        
        composable("practice_listening") {
            ListeningPracticeScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("practice_quiz/{char}/{isRandom}") { backStackEntry ->
            val charArg = backStackEntry.arguments?.getString("char")
            val isRandom = backStackEntry.arguments?.getString("isRandom") == "true"
            val character = if (charArg != "_" && !charArg.isNullOrEmpty()) charArg[0] else null
            PracticeQuizScreen(
                character = character,
                isRandomMode = isRandom,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("challenges_menu") {
            ChallengesMenuScreen(
                onRacerClick = { navController.navigate("challenge") },
                onKeyerClick = { navController.navigate("keyer_challenge") },
                onLeaderboardClick = { navController.navigate("leaderboard") },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("challenge") {
            ChallengeScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("keyer_challenge") {
            KeyerChallengeScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("lesson_list") {
            LessonListScreen(
                onLessonClick = { lessonId ->
                    navController.navigate("lesson/$lessonId")
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("lesson/{lessonId}") { backStackEntry ->
            val lessonId = backStackEntry.arguments?.getString("lessonId") ?: return@composable
            LessonScreen(
                lessonId = lessonId,
                onBackClick = { navController.popBackStack() }
            )
        }

        // New Glossary Screen
        composable("glossary") {
            GlossaryScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("profile") {
            ProfileScreen(
                onLogoutClick = {
                    auth.signOut()
                    navController.popBackStack()
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable("translate") {
            TranslateScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable("leaderboard") {
            LeaderboardScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}