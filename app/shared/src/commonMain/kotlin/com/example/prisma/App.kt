package com.example.prisma

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prisma.dashboard.DashboardScreen
import com.example.prisma.dashboard.DashboardViewModel
import com.example.prisma.dashboard.KtorDashboardRepository
import com.example.prisma.ui.AppTheme
import com.example.prisma.ui.LoginScreen

@Composable
fun App() {
    AppTheme {
        var isAuthenticated by remember { mutableStateOf(false) }
        var userToken by remember { mutableStateOf("") }

        if (!isAuthenticated) {
            LoginScreen(
                onLoginSuccess = { token ->
                    userToken = token
                    isAuthenticated = true
                }
            )
        } else {
            HomeScreen(token = userToken)
        }
    }
}

@Composable
fun HomeScreen(token: String) {
    val viewModel = remember { DashboardViewModel(KtorDashboardRepository()) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(state = state)
}
