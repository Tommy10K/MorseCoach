package com.example.morsecoach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    // Dependencies
    val authRepo = remember { AuthRepository() }

    // UI State
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) } // Toggle between Login/Register
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isRegisterMode) "Create Account" else "Welcome Back",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Email Field
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password Field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Error Message
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Action Button
        Button(
            onClick = {
                isLoading = true
                errorMessage = null
                if (isRegisterMode) {
                    authRepo.register(email, password) { success, error ->
                        isLoading = false
                        if (success) onLoginSuccess() else errorMessage = error
                    }
                } else {
                    authRepo.login(email, password) { success, error ->
                        isLoading = false
                        if (success) onLoginSuccess() else errorMessage = error
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(if (isRegisterMode) "Sign Up" else "Log In")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Toggle Text
        Text(
            text = if (isRegisterMode) "Already have an account? Log In" else "Don't have an account? Sign Up",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                isRegisterMode = !isRegisterMode
                errorMessage = null
            }
        )
    }
}