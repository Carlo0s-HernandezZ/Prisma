package com.example.prisma

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prisma.components.NavDestination
import com.example.prisma.dashboard.DashboardScreen
import com.example.prisma.dashboard.DashboardViewModel
import com.example.prisma.dashboard.KtorDashboardRepository
import com.example.prisma.ui.AppTheme
import com.example.prisma.ui.LoginScreen
import com.example.prisma.ui.ProfileScreen

@Composable
fun App() {
    AppTheme {
        var isAuthenticated by remember { mutableStateOf(false) }
        var userToken by remember { mutableStateOf("") }
        var userEmail by remember { mutableStateOf("") }

        if (!isAuthenticated) {
            LoginScreen(
                onLoginSuccess = { token, email ->
                    userToken = token
                    userEmail = email
                    isAuthenticated = true
                }
            )
        } else {
            HomeScreen(
                token = userToken,
                userEmail = userEmail,
                onLogout = {
                    isAuthenticated = false
                    userToken = ""
                    userEmail = ""
                }
            )
        }
    }
}

@Composable
fun HomeScreen(
    token: String,
    userEmail: String,
    onLogout: () -> Unit
) {
    var selectedDestination by remember { mutableStateOf(NavDestination.Home) }
    val viewModel = remember(userEmail) { DashboardViewModel(KtorDashboardRepository(), userEmail) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (selectedDestination) {
        NavDestination.Profile -> {
            ProfileScreen(
                userEmail = userEmail,
                onNavigate = { selectedDestination = it },
                onLogout = onLogout
            )
        }
        else -> {
            DashboardScreen(
                state = state,
                onNavigate = { selectedDestination = it }
            )
        }
    }
}
