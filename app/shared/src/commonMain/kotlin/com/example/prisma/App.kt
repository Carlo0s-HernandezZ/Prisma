package com.example.prisma

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prisma.components.BottomNavBar
import com.example.prisma.ui.ComingSoonScreen
import com.example.prisma.ui.Prisma
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.prisma.auth.KtorAuthRepository
import com.example.prisma.components.NavDestination
import com.example.prisma.dashboard.DashboardScreen
import com.example.prisma.dashboard.DashboardViewModel
import com.example.prisma.dashboard.KtorDashboardRepository
import com.example.prisma.profile.KtorProfileRepository
import com.example.prisma.profile.ProfileViewModel
import com.example.prisma.ui.AppTheme
import com.example.prisma.ui.LoginScreen
import com.example.prisma.ui.ProfileScreen
import kotlinx.coroutines.launch

private data class UserSession(val token: String, val email: String)

@Composable
fun App() {
    AppTheme {
        var session by remember { mutableStateOf<UserSession?>(null) }
        val authRepository = remember { KtorAuthRepository() }
        val scope = rememberCoroutineScope()

        val current = session
        if (current == null) {
            LoginScreen(
                onLoginSuccess = { token, email -> session = UserSession(token, email) }
            )
        } else {
            HomeScreen(
                token = current.token,
                userEmail = current.email,
                onLogout = {
                    session = null
                    scope.launch { authRepository.logout(current.token) }
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
    // La clave incluye el token: cada sesión obtiene ViewModels nuevos (sin datos de la sesión anterior).
    val dashboardViewModel = viewModel(key = "dashboard-$token") { DashboardViewModel(KtorDashboardRepository(token)) }

    LaunchedEffect(selectedDestination) {
        if (selectedDestination == NavDestination.Home) {
            dashboardViewModel.refresh()
        }
    }

    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val currentOnLogout by rememberUpdatedState(onLogout)

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) currentOnLogout()
    }

    Box(Modifier.fillMaxSize().background(Prisma.colors.background)) {
        // Transición entre ventanas: desliza hacia el lado de la pestaña elegida y hace fundido.
        AnimatedContent(
            targetState = selectedDestination,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                val enter = slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { w -> direction * w / 4 } +
                    fadeIn(tween(320))
                val exit = slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { w -> -direction * w / 4 } +
                    fadeOut(tween(200))
                enter togetherWith exit
            },
            label = "navContent",
        ) { destination ->
            when (destination) {
                NavDestination.Home -> DashboardScreen(
                    state = state,
                    onRetry = dashboardViewModel::refresh,
                )
                NavDestination.Profile -> {
                    val profileViewModel = viewModel(key = "profile-$token") { ProfileViewModel(KtorProfileRepository(token)) }
                    val profileState by profileViewModel.state.collectAsStateWithLifecycle()
                    LaunchedEffect(profileState.sessionExpired) {
                        if (profileState.sessionExpired) currentOnLogout()
                    }
                    ProfileScreen(
                        userEmail = userEmail,
                        viewModel = profileViewModel,
                        onLogout = onLogout,
                    )
                }
                else -> ComingSoonScreen(destination)
            }
        }

        // Una sola barra para toda la app: así el indicador se desliza entre pestañas.
        BottomNavBar(
            selected = selectedDestination,
            onSelect = { selectedDestination = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 448.dp),
        )
    }
}
