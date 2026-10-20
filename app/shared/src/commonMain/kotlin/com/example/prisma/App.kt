package com.example.prisma

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.runtime.*
import com.example.prisma.ui.LoginScreen

@Composable
fun App() {
    // Estado para controlar si el usuario ya inició sesión correctamente
    var isAuthenticated by remember { mutableStateOf(false) }
    var userToken by remember { mutableStateOf("") }

    if (!isAuthenticated) {
        // Muestra la pantalla de inicio de sesión con 2FA
        LoginScreen(
            onLoginSuccess = { token ->
                userToken = token
                isAuthenticated = true
            }
        )
    } else {
        // Pantalla principal a la que entra tras iniciar sesión exitosamente
        HomeScreen(token = userToken)
    }
}

@Composable
fun HomeScreen(token: String) {
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = "¡Bienvenido a PRISMA!\nSesión iniciada correctamente.",
            fontSize = 20.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}
