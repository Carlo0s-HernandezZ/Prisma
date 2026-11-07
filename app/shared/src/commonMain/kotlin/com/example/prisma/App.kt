
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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

import com.example.prisma.auth.KtorAuthRepository
import com.example.prisma.biometric.BiometricController
import com.example.prisma.biometric.BiometricEnableResult
import com.example.prisma.biometric.BiometricKind
import com.example.prisma.biometric.rememberBiometricController
import com.example.prisma.components.BottomNavBar
import com.example.prisma.components.NavDestination
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
import com.example.prisma.ui.CardsScreen
import com.example.prisma.ui.ComingSoonScreen
import com.example.prisma.ui.LoginScreen
import com.example.prisma.ui.Prisma
import com.example.prisma.ui.ProfileScreen

import kotlinx.coroutines.launch

// ====================================================
// SESIÓN DEL USUARIO
// ====================================================

private data class UserSession(
    val token: String,
    val email: String,
    val viaBiometrics: Boolean = false
)

// ====================================================
// APLICACIÓN PRINCIPAL
// ====================================================

@Composable
fun App() {

    AppTheme {

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

        // --------------------------------------------
        // ESTADO DEL ACCESO BIOMÉTRICO
        // --------------------------------------------

        LaunchedEffect(current) {

            offerBiometric =
                current != null &&
                        !current.viaBiometrics &&
                        biometric.onTwoFactorLogin(
                            current.token,
                            current.email
                        )
        }

        fun enableBiometrics(s: UserSession) {

            biometricBusy = true

            scope.launch {

                val message = when (
                    val result = biometric.enable(
                        s.token,
                        s.email
                    )
                ) {

                    BiometricEnableResult.Enabled ->
                        "Listo. La próxima vez entra con tu " +
                                biometric.label + "."

                    BiometricEnableResult.Cancelled ->
                        "No se activó el acceso con " +
                                biometric.label + "."

                    is BiometricEnableResult.Error ->
                        result.message
                }

                biometricBusy = false
                snackbar.showSnackbar(message)
            }
        }

        // --------------------------------------------
        // CONTENIDO DE LA APLICACIÓN
        // --------------------------------------------

        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            if (current == null) {

                LoginScreen(
                    onLoginSuccess = { token, email ->
                        session = UserSession(
                            token = token,
                            email = email
                        )
                    },

                    onBiometricLogin = { s ->
                        session = UserSession(
                            token = s.accessToken,
                            email = s.email,
                            viaBiometrics = true
                        )
                    },

                    biometric = biometric,

                    autoPromptBiometric =
                        autoPromptBiometric,

                    onAutoPromptConsumed = {
                        autoPromptBiometric = false
                    }
                )

            } else {

                val biometricSetting =
                    if (biometric.isSupported) {

                        BiometricSetting(
                            label = biometric.label,

                            isFace =
                                biometric.kind ==
                                        BiometricKind.FaceId,

                            enabled =
                                biometric.enrolledEmail.equals(
                                    current.email,
                                    ignoreCase = true
                                ),

                            busy = biometricBusy,

                            onToggle = { enable ->

                                if (enable) {

                                    enableBiometrics(current)

                                } else {

                                    biometricBusy = true

                                    scope.launch {

                                        biometric.disable(
                                            current.token
                                        )

                                        biometricBusy = false

                                        snackbar.showSnackbar(
                                            "Acceso con " +
                                                    biometric.label +
                                                    " desactivado."
                                        )
                                    }
                                }
                            }
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

                        scope.launch {
                            authRepository.logout(
                                current.token
                            )
                        }
                    }
                )

                // ------------------------------------
                // ACTIVACIÓN DE BIOMETRÍA
                // ------------------------------------

                if (offerBiometric) {

                    AlertDialog(
                        onDismissRequest = {},

                        icon = {
                            Icon(
                                imageVector = if (
                                    biometric.kind ==
                                    BiometricKind.FaceId
                                ) {
                                    Icons.Filled.Face
                                } else {
                                    Icons.Filled.Fingerprint
                                },
                                contentDescription = null
                            )
                        },

                        title = {
                            Text(
                                "¿Entrar con tu " +
                                        biometric.label + "?"
                            )
                        },

                        text = {
                            Text(
                                "La próxima vez podrás entrar " +
                                        "a Prisma solo con tu " +
                                        biometric.label +
                                        ", sin escribir tu contraseña." +
                                        "\n\n" +
                                        BiometricController.windowExplanation
                            )
                        },

                        confirmButton = {

                            TextButton(
                                onClick = {

                                    offerBiometric = false

                                    enableBiometrics(current)
                                }
                            ) {
                                Text("Activar")
                            }
                        },

                        dismissButton = {

                            TextButton(
                                onClick = {

                                    offerBiometric = false

                                    biometric.decline(
                                        current.email
                                    )

                                    scope.launch {
                                        snackbar.showSnackbar(
                                            "Puedes activarlo cuando " +
                                                    "quieras desde tu Perfil."
                                        )
                                    }
                                }
                            ) {
                                Text("Ahora no")
                            }
                        }
                    )
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(16.dp)
            )
        }
    }
}

// ====================================================
// NAVEGACIÓN PRINCIPAL
// ====================================================

@Composable
fun HomeScreen(
    token: String,
    userEmail: String,
    onLogout: () -> Unit,
    biometricSetting: BiometricSetting? = null
) {

    var selectedDestination by remember {
        mutableStateOf(NavDestination.Home)
    }

    // --------------------------------------------
    // SELECTOR DE NÓMINA
    // --------------------------------------------

    val documentPicker =
        LocalPayrollDocumentPicker.current

    var documentoSeleccionado by remember(token) {
        mutableStateOf<PayrollSelectedFile?>(null)
    }

    var contadorDocumentos by remember(token) {
        mutableLongStateOf(0L)
    }

    // --------------------------------------------
    // VIEWMODEL DEL DASHBOARD
    // --------------------------------------------

    val dashboardViewModel = viewModel(
        key = "dashboard-$token"
    ) {
        DashboardViewModel(
            KtorDashboardRepository(token)
        )
    }

    LaunchedEffect(selectedDestination) {

        if (selectedDestination == NavDestination.Home) {
            dashboardViewModel.refresh()
        }
    }

    val state by dashboardViewModel.uiState
        .collectAsStateWithLifecycle()

    val currentOnLogout by rememberUpdatedState(onLogout)

    LaunchedEffect(state.sessionExpired) {

        if (state.sessionExpired) {
            currentOnLogout()
        }
    }

    // --------------------------------------------
    // CONTENIDO PRINCIPAL
    // --------------------------------------------

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Prisma.colors.background)
    ) {

        AnimatedContent(
            targetState = selectedDestination,
            modifier = Modifier.fillMaxSize(),

            transitionSpec = {

                val direction = if (
                    targetState.ordinal >
                    initialState.ordinal
                ) {
                    1
                } else {
                    -1
                }

                val enter =
                    slideInHorizontally(
                        tween(
                            320,
                            easing = FastOutSlowInEasing
                        )
                    ) { width ->
                        direction * width / 4
                    } + fadeIn(tween(320))

                val exit =
                    slideOutHorizontally(
                        tween(
                            320,
                            easing = FastOutSlowInEasing
                        )
                    ) { width ->
                        -direction * width / 4
                    } + fadeOut(tween(200))

                enter togetherWith exit
            },

            label = "navContent"
        ) { destination ->

            when (destination) {

                // ------------------------------------
                // INICIO
                // ------------------------------------

                NavDestination.Home -> {

                    DashboardScreen(
                        state = state,
                        onRetry =
                            dashboardViewModel::refresh
                    )
                }

                // ------------------------------------
                // PERFIL
                // ------------------------------------

                NavDestination.Profile -> {

                    val profileViewModel = viewModel(
                        key = "profile-$token"
                    ) {
                        ProfileViewModel(
                            KtorProfileRepository(token)
                        )
                    }

                    val profileState by profileViewModel
                        .state.collectAsStateWithLifecycle()

                    LaunchedEffect(
                        profileState.sessionExpired
                    ) {
                        if (profileState.sessionExpired) {
                            currentOnLogout()
                        }
                    }

                    ProfileScreen(
                        userEmail = userEmail,
                        viewModel = profileViewModel,
                        onLogout = onLogout,
                        biometric = biometricSetting
                    )
                }

                // ------------------------------------
                // TARJETAS
                // ------------------------------------

                NavDestination.Cards -> {

                    val incomeViewModel = viewModel(
                        key = "incomes-$token"
                    ) {
                        IncomeViewModel(
                            KtorIncomeRepository(token)
                        )
                    }

                    val incomeState by incomeViewModel
                        .state.collectAsStateWithLifecycle()

                    LaunchedEffect(
                        incomeState.sessionExpired
                    ) {
                        if (incomeState.sessionExpired) {
                            currentOnLogout()
                        }
                    }

                    CardsScreen(incomeViewModel)
                }

                // ------------------------------------
                // ASISTENTE IA
                // ------------------------------------

                NavDestination.Assistant -> {

                    PayrollScreen(
                        token = token,

                        documentoSeleccionado =
                            documentoSeleccionado,

                        onSeleccionarDocumento =
                            if (documentPicker != null) {

                                {
                                    documentPicker.open {
                                            nombre,
                                            contenido ->

                                        contadorDocumentos += 1L

                                        documentoSeleccionado =
                                            PayrollSelectedFile(
                                                identificador =
                                                    contadorDocumentos,
                                                nombreArchivo =
                                                    nombre,
                                                contenido =
                                                    contenido
                                            )
                                    }
                                }

                            } else {
                                null
                            }
                    )
                }

                // ------------------------------------
                // OTRAS PANTALLAS
                // ------------------------------------

                else -> {
                    ComingSoonScreen(destination)
                }
            }
        }

        // --------------------------------------------
        // BARRA INFERIOR
        // --------------------------------------------

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
                                WindowInsetsSides.Horizontal
                    )
                )
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
                .widthIn(max = 448.dp)
        )
    }
}
