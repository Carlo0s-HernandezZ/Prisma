package com.example.prisma

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.prisma.api.CardDto
import com.example.prisma.auth.KtorAuthRepository
import com.example.prisma.biometric.BiometricEnableResult
import com.example.prisma.biometric.BiometricKind
import com.example.prisma.biometric.rememberBiometricController
import com.example.prisma.budget.BudgetViewModel
import com.example.prisma.budget.KtorBudgetRepository
import com.example.prisma.cards.CardDetailViewModel
import com.example.prisma.cards.CardViewModel
import com.example.prisma.cards.KtorCardRepository
import com.example.prisma.components.BottomNavBar
import com.example.prisma.components.NavDestination
import com.example.prisma.components.SideNavBar
import com.example.prisma.dashboard.DashboardScreen
import com.example.prisma.dashboard.DashboardViewModel
import com.example.prisma.dashboard.KtorDashboardRepository
import com.example.prisma.income.IncomeViewModel
import com.example.prisma.income.KtorIncomeRepository
import com.example.prisma.payroll.LocalPayrollDocumentPicker
import com.example.prisma.payroll.PayrollScreen
import com.example.prisma.payroll.PayrollSelectedFile
import com.example.prisma.profile.KtorProfileRepository
import com.example.prisma.profile.ProfileViewModel
import com.example.prisma.ui.AppTheme
import com.example.prisma.ui.BiometricSetting
import com.example.prisma.ui.CardDetailScreen
import com.example.prisma.ui.CardPlansScreen
import com.example.prisma.ui.CardsScreen
import com.example.prisma.ui.ComingSoonScreen
import com.example.prisma.ui.LoginScreen
import com.example.prisma.ui.Prisma
import com.example.prisma.ui.ProfileScreen
import com.example.prisma.ui.ProvideLayoutMode
import com.example.prisma.ui.SplashScreen
import com.example.prisma.ui.isDesktopLayout
import kotlinx.coroutines.launch

/**
 * Sesión activa de la aplicación.
 *
 * viaBiometrics indica que el acceso se realizó con huella o Face ID.
 */
private data class UserSession(
    val token: String,
    val email: String,
    val viaBiometrics: Boolean = false,
)

/**
 * Punto de entrada de la interfaz.
 *
 * Aplica el tema y muestra la animación inicial antes del contenido.
 */
@Composable
fun App() {
    var splashDone by rememberSaveable {
        mutableStateOf(false)
    }

    AppTheme {
        Crossfade(
            targetState = splashDone,
            animationSpec = tween(350),
            label = "splash",
        ) { done ->
            if (done) {
                AppContent()
            } else {
                SplashScreen(
                    onFinished = {
                        splashDone = true
                    },
                )
            }
        }
    }
}

/**
 * Controla el acceso a la aplicación y la configuración biométrica.
 */
@Composable
private fun AppContent() {
    ProvideLayoutMode {
        // La sesión actual permanece en memoria.
        var session by remember {
            mutableStateOf<UserSession?>(null)
        }

        val authRepository = remember {
            KtorAuthRepository()
        }

        val biometric = rememberBiometricController()
        val scope = rememberCoroutineScope()

        val snackbar = remember {
            SnackbarHostState()
        }

        // El intento automático de biometría se consume una sola vez.
        var autoPromptBiometric by remember {
            mutableStateOf(true)
        }

        var offerBiometric by remember {
            mutableStateOf(false)
        }

        var biometricBusy by remember {
            mutableStateOf(false)
        }

        val current = session

        // Ofrece configurar biometría después de un acceso compatible.
        LaunchedEffect(current) {
            offerBiometric = current != null &&
                    !current.viaBiometrics &&
                    biometric.onTwoFactorLogin(
                        current.token,
                        current.email,
                    )
        }

        // Utiliza el controlador biométrico existente de cada plataforma.
        fun enableBiometrics(userSession: UserSession) {
            biometricBusy = true

            scope.launch {
                val message = when (
                    val result = biometric.enable(
                        userSession.token,
                        userSession.email,
                    )
                ) {
                    BiometricEnableResult.Enabled ->
                        "Listo. La próxima vez entra con tu ${biometric.label}."

                    BiometricEnableResult.Cancelled ->
                        "No se activó el acceso con ${biometric.label}."

                    is BiometricEnableResult.Error ->
                        result.message
                }

                biometricBusy = false
                snackbar.showSnackbar(message)
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Sin sesión se muestra la pantalla de acceso.
            if (current == null) {
                LoginScreen(
                    onLoginSuccess = { token, email ->
                        session = UserSession(
                            token = token,
                            email = email,
                        )
                    },
                    onBiometricLogin = { biometricSession ->
                        session = UserSession(
                            token = biometricSession.accessToken,
                            email = biometricSession.email,
                            viaBiometrics = true,
                        )
                    },
                    biometric = biometric,
                    autoPromptBiometric = autoPromptBiometric,
                    onAutoPromptConsumed = {
                        autoPromptBiometric = false
                    },
                )
            } else {
                // Configuración de biometría disponible desde Perfil.
                val biometricSetting = if (biometric.isSupported) {
                    BiometricSetting(
                        label = biometric.label,
                        isFace = biometric.kind == BiometricKind.FaceId,
                        enabled = biometric.enrolledEmail.equals(
                            current.email,
                            ignoreCase = true,
                        ),
                        busy = biometricBusy,
                        onToggle = { enable ->
                            if (enable) {
                                enableBiometrics(current)
                            } else {
                                biometricBusy = true

                                scope.launch {
                                    biometric.disable(current.token)
                                    biometricBusy = false

                                    snackbar.showSnackbar(
                                        "Acceso con ${biometric.label} desactivado.",
                                    )
                                }
                            }
                        },
                    )
                } else {
                    null
                }

                // Una sesión válida permite abrir la navegación principal.
                HomeScreen(
                    token = current.token,
                    userEmail = current.email,
                    biometricSetting = biometricSetting,
                    onLogout = {
                        // Cierra primero la sesión local.
                        session = null
                        offerBiometric = false

                        // Solicita también el cierre de la sesión al servidor.
                        scope.launch {
                            authRepository.logout(current.token)
                        }
                    },
                )

                // Diálogo existente para activar huella o Face ID.
                if (offerBiometric) {
                    AlertDialog(
                        onDismissRequest = {},
                        icon = {
                            Icon(
                                imageVector = if (
                                    biometric.kind == BiometricKind.FaceId
                                ) {
                                    Icons.Filled.Face
                                } else {
                                    Icons.Filled.Fingerprint
                                },
                                contentDescription = null,
                            )
                        },
                        title = {
                            Text(
                                text = "¿Entrar con tu ${biometric.label}?",
                            )
                        },
                        text = {
                            Text(
                                text = "La próxima vez podrás entrar a Prisma " +
                                        "solo con tu ${biometric.label}, " +
                                        "sin escribir tu contraseña.\n\n",
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    offerBiometric = false
                                    enableBiometrics(current)
                                },
                            ) {
                                Text("Activar")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    offerBiometric = false
                                    biometric.decline(current.email)

                                    scope.launch {
                                        snackbar.showSnackbar(
                                            "Puedes activarlo cuando quieras " +
                                                    "desde tu Perfil.",
                                        )
                                    }
                                },
                            ) {
                                Text("Ahora no")
                            }
                        },
                    )
                }
            }

            // Mensajes breves de las operaciones de biometría.
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(16.dp),
            )
        }
    }
}

/**
 * Navegación principal.
 *
 * Inicio:
 * - Muestra el dashboard existente.
 *
 * Tarjetas:
 * - Lista de tarjetas e ingresos.
 * - Detalle y lectura automática de estados.
 * - Acceso a auditoría de nómina.
 *
 * Análisis:
 * - Planes de pago por tarjeta.
 * - Total programado del mes.
 * - Acceso a configuración de dinero, gastos y reserva.
 */
@Composable
fun HomeScreen(
    token: String,
    userEmail: String,
    onLogout: () -> Unit,
    biometricSetting: BiometricSetting? = null,
) {
    // La selección se reinicia si cambia la sesión.
    var selectedDestination by remember(token) {
        mutableStateOf(NavDestination.Home)
    }

    // Selector existente de recibos PDF/XML para auditoría de nómina.
    val documentPicker = LocalPayrollDocumentPicker.current

    var documentoSeleccionado by remember(token) {
        mutableStateOf<PayrollSelectedFile?>(null)
    }

    var contadorDocumentos by remember(token) {
        mutableStateOf(0L)
    }

    // Se conserva el ViewModel actual del dashboard.
    val dashboardViewModel = viewModel(
        key = "dashboard-$token",
    ) {
        DashboardViewModel(
            KtorDashboardRepository(token),
        )
    }

    // Actualiza los datos al regresar a Inicio.
    LaunchedEffect(selectedDestination) {
        if (selectedDestination == NavDestination.Home) {
            dashboardViewModel.refresh()
        }
    }

    val state by dashboardViewModel.uiState
        .collectAsStateWithLifecycle()

    // Permite que los efectos utilicen el callback de cierre actualizado.
    val currentOnLogout by rememberUpdatedState(onLogout)

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            currentOnLogout()
        }
    }

    val desktop = isDesktopLayout

    /*
     * Contenido compartido entre móvil y escritorio.
     *
     * La distribución externa cambia, pero las pantallas y las
     * transiciones pertenecen a esta misma navegación.
     */
    val screens: @Composable (Modifier) -> Unit = { contentModifier ->
        AnimatedContent(
            targetState = selectedDestination,
            modifier = contentModifier,
            transitionSpec = {
                val direction = if (
                    targetState.ordinal > initialState.ordinal
                ) {
                    1
                } else {
                    -1
                }

                val spec = tween<IntOffset>(
                    durationMillis = 320,
                    easing = FastOutSlowInEasing,
                )

                val enterSlide = if (desktop) {
                    slideInVertically(spec) { height ->
                        direction * height / 6
                    }
                } else {
                    slideInHorizontally(spec) { width ->
                        direction * width / 4
                    }
                }

                val exitSlide = if (desktop) {
                    slideOutVertically(spec) { height ->
                        -direction * height / 6
                    }
                } else {
                    slideOutHorizontally(spec) { width ->
                        -direction * width / 4
                    }
                }

                val enter = enterSlide + fadeIn(tween(320))
                val exit = exitSlide + fadeOut(tween(200))

                enter togetherWith exit
            },
            label = "navContent",
        ) { destination ->
            when (destination) {
                // ---------------------------------------------------
                // Inicio
                // ---------------------------------------------------
                NavDestination.Home -> {
                    DashboardScreen(
                        state = state,
                        onRetry = dashboardViewModel::refresh,
                    )
                }

                // ---------------------------------------------------
                // Perfil
                // ---------------------------------------------------
                NavDestination.Profile -> {
                    val profileViewModel = viewModel(
                        key = "profile-$token",
                    ) {
                        ProfileViewModel(
                            KtorProfileRepository(token),
                        )
                    }

                    val profileState by profileViewModel.state
                        .collectAsStateWithLifecycle()

                    LaunchedEffect(profileState.sessionExpired) {
                        if (profileState.sessionExpired) {
                            currentOnLogout()
                        }
                    }

                    ProfileScreen(
                        userEmail = userEmail,
                        viewModel = profileViewModel,
                        onLogout = onLogout,
                        biometric = biometricSetting,
                    )
                }

                // ---------------------------------------------------
                // Tarjetas, ingresos y auditoría de nómina
                // ---------------------------------------------------
                NavDestination.Cards -> {
                    // Controla qué tarjeta se encuentra abierta.
                    var selectedCard by remember(token) {
                        mutableStateOf<CardDto?>(null)
                    }

                    // La auditoría se abre desde la sección financiera.
                    var showingPayroll by remember(token) {
                        mutableStateOf(false)
                    }

                    /*
                     * Conserva los estados guardables de CardsScreen
                     * mientras se abre la auditoría o el detalle y se regresa.
                     */
                    val financeScreenState =
                        rememberSaveableStateHolder()

                    val cardViewModel = viewModel(
                        key = "cards-$token",
                    ) {
                        CardViewModel(
                            KtorCardRepository(token),
                        )
                    }

                    val cardState by cardViewModel.state
                        .collectAsStateWithLifecycle()

                    val incomeViewModel = viewModel(
                        key = "incomes-$token",
                    ) {
                        IncomeViewModel(
                            KtorIncomeRepository(token),
                        )
                    }

                    val incomeState by incomeViewModel.state
                        .collectAsStateWithLifecycle()

                    // Las dos fuentes pueden detectar una sesión vencida.
                    LaunchedEffect(
                        cardState.sessionExpired,
                        incomeState.sessionExpired,
                    ) {
                        if (
                            cardState.sessionExpired ||
                            incomeState.sessionExpired
                        ) {
                            currentOnLogout()
                        }
                    }

                    val card = selectedCard

                    when {
                        // Auditoría de nómina.
                        showingPayroll -> {
                            PayrollScreen(
                                token = token,
                                documentoSeleccionado = documentoSeleccionado,
                                onSeleccionarDocumento =
                                    documentPicker?.let { picker ->
                                        {
                                            picker.open { nombre, contenido ->
                                                contadorDocumentos += 1L

                                                documentoSeleccionado =
                                                    PayrollSelectedFile(
                                                        identificador =
                                                            contadorDocumentos,
                                                        nombreArchivo = nombre,
                                                        contenido = contenido,
                                                    )
                                            }
                                        }
                                    },
                                onBack = {
                                    showingPayroll = false
                                },
                            )
                        }

                        // Detalle de una tarjeta y su estado de cuenta.
                        card != null -> {
                            val detailViewModel = viewModel(
                                key = "card-detail-${card.id}-$token",
                            ) {
                                CardDetailViewModel(
                                    card,
                                    KtorCardRepository(token),
                                )
                            }

                            CardDetailScreen(
                                viewModel = detailViewModel,
                                onBack = {
                                    selectedCard = null
                                },
                                token = token,
                                onSessionExpired = {
                                    currentOnLogout()
                                },
                                onViewPlans = {
                                    /*
                                     * El botón "Ver en Análisis" del detalle
                                     * abre la pantalla global de planes.
                                     */
                                    selectedDestination =
                                        NavDestination.Assistant
                                },
                            )
                        }

                        // Lista principal de tarjetas e ingresos.
                        else -> {
                            financeScreenState.SaveableStateProvider(
                                key = "finance",
                            ) {
                                CardsScreen(
                                    cardViewModel = cardViewModel,
                                    incomeViewModel = incomeViewModel,
                                    onCardClick = { selected ->
                                        selectedCard = selected
                                    },
                                    onPayrollClick = {
                                        showingPayroll = true
                                    },
                                )
                            }
                        }
                    }
                }

                // ---------------------------------------------------
                // Análisis y planes de pago
                // ---------------------------------------------------
                /*
                 * Se conserva Assistant como identificador interno.
                 * Su nombre visible en la navegación es Análisis.
                 */
                NavDestination.Assistant -> {
                    val budgetViewModel = viewModel(
                        key = "budget-$token",
                    ) {
                        BudgetViewModel(
                            KtorBudgetRepository(token),
                        )
                    }

                    val budgetState by budgetViewModel.state
                        .collectAsStateWithLifecycle()

                    LaunchedEffect(budgetState.sessionExpired) {
                        if (budgetState.sessionExpired) {
                            currentOnLogout()
                        }
                    }

                    /*
                     * Esta es la única pantalla principal de Análisis.
                     *
                     * CardPlansScreen muestra los planes y abre internamente
                     * AnalysisScreen al configurar dinero, gastos y reserva.
                     *
                     * No se dibuja AnalysisScreen aquí, porque ambas
                     * pantallas terminarían superpuestas.
                     */
                    CardPlansScreen(
                        viewModel = budgetViewModel,
                        token = token,
                        onSessionExpired = {
                            currentOnLogout()
                        },
                    )
                }

                // Destinos que todavía utilizan la pantalla de próximo acceso.
                else -> {
                    ComingSoonScreen(destination)
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Distribución de escritorio
    // ---------------------------------------------------------------
    if (desktop) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Prisma.colors.background),
        ) {
            SideNavBar(
                selected = selectedDestination,
                onSelect = {
                    selectedDestination = it
                },
                userEmail = userEmail,
                onLogout = onLogout,
            )

            screens(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }

        return
    }

    // ---------------------------------------------------------------
    // Distribución móvil con navegación flotante
    // ---------------------------------------------------------------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Prisma.colors.background),
    ) {
        screens(
            Modifier.fillMaxSize(),
        )

        BottomNavBar(
            selected = selectedDestination,
            onSelect = {
                selectedDestination = it
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom +
                                WindowInsetsSides.Horizontal,
                    ),
                )
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                )
                .widthIn(max = 448.dp),
        )
    }
}