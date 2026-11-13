package com.example.prisma

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.prisma.ui.SplashScreen
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
import com.example.prisma.cards.CardViewModel
import com.example.prisma.cards.KtorCardRepository
import com.example.prisma.cards.CardDetailViewModel
import com.example.prisma.api.CardDto
import com.example.prisma.ui.CardDetailScreen
import com.example.prisma.income.IncomeViewModel
import com.example.prisma.income.KtorIncomeRepository
import com.example.prisma.ui.CardsScreen
import com.example.prisma.ui.ComingSoonScreen
import com.example.prisma.payroll.LocalPayrollDocumentPicker
import com.example.prisma.payroll.PayrollScreen
import com.example.prisma.payroll.PayrollSelectedFile
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.prisma.biometric.BiometricController
import com.example.prisma.biometric.BiometricEnableResult
import com.example.prisma.biometric.BiometricKind
import com.example.prisma.biometric.rememberBiometricController
import com.example.prisma.ui.BiometricSetting
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.IntOffset
import com.example.prisma.components.SideNavBar
import com.example.prisma.ui.ProvideLayoutMode
import com.example.prisma.ui.isDesktopLayout

/** [viaBiometrics]: true si se entró con huella / Face ID (sin escribir contraseña ni código). */
private data class UserSession(val token: String, val email: String, val viaBiometrics: Boolean = false)

@Composable
fun App() {
    // Animación de inicio «TDSOFT presenta · Prisma» (una vez por apertura; sobrevive a la rotación).
    var splashDone by rememberSaveable { mutableStateOf(false) }
    AppTheme {
        Crossfade(targetState = splashDone, animationSpec = tween(350), label = "splash") { done ->
            if (done) AppContent() else SplashScreen(onFinished = { splashDone = true })
        }
    }
}

@Composable
private fun AppContent() {
      ProvideLayoutMode {
        var session by remember { mutableStateOf<UserSession?>(null) }
        val authRepository = remember { KtorAuthRepository() }
        val biometric = rememberBiometricController()
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }

        // Al abrir la app (no tras cerrar sesión) se muestra directamente el diálogo de huella / Face ID.
        var autoPromptBiometric by remember { mutableStateOf(true) }
        var offerBiometric by remember { mutableStateOf(false) }
        var biometricBusy by remember { mutableStateOf(false) }

        val current = session

        // Tras un login con contraseña + 2FA: renovar la ventana de 24 h o preguntar si se quiere activar.
        LaunchedEffect(current) {
            offerBiometric = current != null && !current.viaBiometrics &&
                biometric.onTwoFactorLogin(current.token, current.email)
        }

        fun enableBiometrics(s: UserSession) {
            biometricBusy = true
            scope.launch {
                val message = when (val r = biometric.enable(s.token, s.email)) {
                    BiometricEnableResult.Enabled -> "Listo. La próxima vez entra con tu ${biometric.label}."
                    BiometricEnableResult.Cancelled -> "No se activó el acceso con ${biometric.label}."
                    is BiometricEnableResult.Error -> r.message
                }
                biometricBusy = false
                snackbar.showSnackbar(message)
            }
        }

        Box(Modifier.fillMaxSize()) {
            if (current == null) {
                LoginScreen(
                    onLoginSuccess = { token, email -> session = UserSession(token, email) },
                    onBiometricLogin = { s -> session = UserSession(s.accessToken, s.email, viaBiometrics = true) },
                    biometric = biometric,
                    autoPromptBiometric = autoPromptBiometric,
                    onAutoPromptConsumed = { autoPromptBiometric = false },
                )
            } else {
                val biometricSetting = if (biometric.isSupported) {
                    BiometricSetting(
                        label = biometric.label,
                        isFace = biometric.kind == BiometricKind.FaceId,
                        enabled = biometric.enrolledEmail.equals(current.email, ignoreCase = true),
                        busy = biometricBusy,
                        onToggle = { enable ->
                            if (enable) {
                                enableBiometrics(current)
                            } else {
                                biometricBusy = true
                                scope.launch {
                                    biometric.disable(current.token)
                                    biometricBusy = false
                                    snackbar.showSnackbar("Acceso con ${biometric.label} desactivado.")
                                }
                            }
                        },
                    )
                } else {
                    null
                }

                HomeScreen(
                    token = current.token,
                    userEmail = current.email,
                    biometricSetting = biometricSetting,
                    onLogout = {
                        session = null
                        offerBiometric = false
                        scope.launch { authRepository.logout(current.token) }
                    }
                )

                if (offerBiometric) {
                    AlertDialog(
                        onDismissRequest = {},
                        icon = {
                            Icon(
                                if (biometric.kind == BiometricKind.FaceId) Icons.Filled.Face else Icons.Filled.Fingerprint,
                                contentDescription = null,
                            )
                        },
                        title = { Text("¿Entrar con tu ${biometric.label}?") },
                        text = {
                            Text(
                                "La próxima vez podrás entrar a Prisma solo con tu ${biometric.label}, sin escribir tu contraseña.\n\n" 

                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                offerBiometric = false
                                enableBiometrics(current)
                            }) { Text("Activar") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                offerBiometric = false
                                biometric.decline(current.email)
                                scope.launch { snackbar.showSnackbar("Puedes activarlo cuando quieras desde tu Perfil.") }
                            }) { Text("Ahora no") }
                        },
                    )
                }
            }

            SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(16.dp))
        }
      }
}

@Composable
fun HomeScreen(
    token: String,
    userEmail: String,
    onLogout: () -> Unit,
    biometricSetting: BiometricSetting? = null,
) {
    var selectedDestination by remember { mutableStateOf(NavDestination.Home) }
    // Selector de recibos de nómina (PDF/XML) para el Asistente IA; solo existe en Android.
    val documentPicker = LocalPayrollDocumentPicker.current
    var documentoSeleccionado by remember(token) { mutableStateOf<PayrollSelectedFile?>(null) }
    var contadorDocumentos by remember(token) { mutableStateOf(0L) }
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

    val desktop = isDesktopLayout

    val screens: @Composable (Modifier) -> Unit = { contentModifier ->
        // Transición entre ventanas: desliza hacia el lado de la sección elegida y hace fundido
        // (horizontal con la barra inferior del móvil, vertical con el menú lateral de escritorio).
        AnimatedContent(
            targetState = selectedDestination,
            modifier = contentModifier,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                val spec = tween<IntOffset>(320, easing = FastOutSlowInEasing)
                val enter = (if (desktop) slideInVertically(spec) { h -> direction * h / 6 }
                    else slideInHorizontally(spec) { w -> direction * w / 4 }) + fadeIn(tween(320))
                val exit = (if (desktop) slideOutVertically(spec) { h -> -direction * h / 6 }
                    else slideOutHorizontally(spec) { w -> -direction * w / 4 }) + fadeOut(tween(200))
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
                        biometric = biometricSetting,
                    )
                }
                NavDestination.Cards -> {
                    var selectedCard by remember { mutableStateOf<CardDto?>(null) }
                    val cardViewModel = viewModel(key = "cards-$token") { CardViewModel(KtorCardRepository(token)) }
                    val cardState by cardViewModel.state.collectAsStateWithLifecycle()
                    val incomeViewModel = viewModel(key = "incomes-$token") { IncomeViewModel(KtorIncomeRepository(token)) }
                    val incomeState by incomeViewModel.state.collectAsStateWithLifecycle()
                    LaunchedEffect(cardState.sessionExpired, incomeState.sessionExpired) {
                        if (cardState.sessionExpired || incomeState.sessionExpired) currentOnLogout()
                    }
                    val card = selectedCard
                    if (card != null) {
                        val detailVm = viewModel(key = "card-detail-${card.id}-$token") { CardDetailViewModel(card, KtorCardRepository(token)) }
                        CardDetailScreen(
                            viewModel = detailVm,
                            onBack = { selectedCard = null }
                        )
                    } else {
                        CardsScreen(cardViewModel, incomeViewModel, onCardClick = { selectedCard = it })
                    }
                }
                NavDestination.Assistant -> PayrollScreen(
                    token = token,
                    documentoSeleccionado = documentoSeleccionado,
                    onSeleccionarDocumento = documentPicker?.let { picker ->
                        {
                            picker.open { nombre, contenido ->
                                contadorDocumentos += 1L
                                documentoSeleccionado = PayrollSelectedFile(
                                    identificador = contadorDocumentos,
                                    nombreArchivo = nombre,
                                    contenido = contenido,
                                )
                            }
                        }
                    },
                )
                else -> ComingSoonScreen(destination)
            }
        }
    }

    if (desktop) {
        // Escritorio (solo web): menú lateral fijo + contenido.
        Row(Modifier.fillMaxSize().background(Prisma.colors.background)) {
            SideNavBar(
                selected = selectedDestination,
                onSelect = { selectedDestination = it },
                userEmail = userEmail,
                onLogout = onLogout,
            )
            screens(Modifier.weight(1f).fillMaxHeight())
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Prisma.colors.background)) {
        screens(Modifier.fillMaxSize())

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
