package com.example.prisma.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.runtime.rememberCoroutineScope
import com.example.prisma.api.ApiRules
import com.example.prisma.api.RegisterResponse
import com.example.prisma.api.Verify2faResponse
import com.example.prisma.biometric.BiometricController
import com.example.prisma.biometric.BiometricKind
import com.example.prisma.biometric.BiometricUnlockResult
import kotlinx.coroutines.launch
import com.example.prisma.getPlatform
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import com.example.prisma.auth.KtorAuthRepository
import com.example.prisma.auth.LoginStep
import com.example.prisma.auth.LoginUiState
import com.example.prisma.auth.LoginViewModel
import com.example.prisma.components.TornPaper

private val PanelColor = PrismaPalette.NaranjaPrisma
private val InkOnPanel = PrismaPalette.Grafito
private val ButtonColor = PrismaPalette.Dorado

@Composable
fun LoginScreen(
    onLoginSuccess: (token: String, email: String) -> Unit,
    modifier: Modifier = Modifier,
    onForgotPassword: () -> Unit = {},
    /** Login con huella / Face ID (sin 2FA, dentro de la ventana de 24 h). */
    onBiometricLogin: (Verify2faResponse) -> Unit = {},
    biometric: BiometricController? = null,
    /** Al abrir la app, mostrar directamente el diálogo de huella / Face ID. */
    autoPromptBiometric: Boolean = false,
    onAutoPromptConsumed: () -> Unit = {},
    viewModel: LoginViewModel = viewModel { LoginViewModel(KtorAuthRepository()) },
) {
    val state by viewModel.state.collectAsState()
    val currentOnSuccess by rememberUpdatedState(onLoginSuccess)
    val currentOnBiometricLogin by rememberUpdatedState(onBiometricLogin)
    val scope = rememberCoroutineScope()
    var biometricBusy by remember { mutableStateOf(false) }
    val biometricAvailable = biometric != null && biometric.isSupported && biometric.enrolledEmail != null

    fun unlockWithBiometrics() {
        val controller = biometric ?: return
        if (biometricBusy) return
        biometricBusy = true
        scope.launch {
            when (val result = controller.unlock()) {
                is BiometricUnlockResult.Success -> currentOnBiometricLogin(result.session)
                is BiometricUnlockResult.TwoFactorRequired -> viewModel.showNotice(
                    "Pasaron ${ApiRules.TWO_FACTOR_WINDOW_HOURS} horas desde tu último código de verificación. " +
                        "Entra con tu contraseña y el código de Google Authenticator.",
                    email = result.email,
                )
                is BiometricUnlockResult.Disabled -> viewModel.showNotice(result.message)
                is BiometricUnlockResult.Error -> viewModel.showNotice(result.message)
                BiometricUnlockResult.Cancelled -> Unit
            }
            biometricBusy = false
        }
    }

    LaunchedEffect(Unit) {
        if (autoPromptBiometric) {
            onAutoPromptConsumed()
            if (biometricAvailable) unlockWithBiometrics()
        }
    }
    LaunchedEffect(state.session) {
        state.session?.let { session ->
            viewModel.onSessionConsumed()
            currentOnSuccess(session.accessToken, session.email)
        }
    }

    val colors = Prisma.colors
    val isLight = colors.background.luminance() > 0.5f

    BoxWithConstraints(modifier.fillMaxSize().background(PanelColor)) {
        val paperHeight = (maxHeight * 0.38f).coerceIn(230.dp, 340.dp)

        Column(
            modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(paperHeight)) {
                TornPaper(
                    paperColor = colors.background,
                    fringeColor = if (isLight) Color(0xFFE6E1D8) else Color(0xFF3A393E),
                    modifier = Modifier.fillMaxSize(),
                )
                PrismaLogo(
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 16.dp)
                        .height(paperHeight * 0.56f),
                )
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .padding(top = 8.dp, bottom = 16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.registerResult != null) {
                    RegisterSuccessForm(
                        result = state.registerResult!!,
                        onDismiss = viewModel::dismissRegisterResult
                    )
                } else {
                    AnimatedContent(
                        targetState = state.step,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                    ) { step ->
                        when (step) {
                            LoginStep.Credentials -> CredentialsForm(
                                state = state,
                                onEmailChange = viewModel::onEmailChange,
                                onPasswordChange = viewModel::onPasswordChange,
                                onTogglePassword = viewModel::togglePasswordVisibility,
                                onSubmit = viewModel::submit,
                                onToggleRegister = viewModel::toggleRegisterMode,
                                onForgotPassword = onForgotPassword,
                                biometricLabel = if (biometricAvailable) biometric?.label else null,
                                biometricIsFace = biometric?.kind == BiometricKind.FaceId,
                                biometricBusy = biometricBusy,
                                onBiometric = ::unlockWithBiometrics,
                            )
                            LoginStep.TwoFactor -> TwoFactorForm(
                                state = state,
                                onCodeChange = viewModel::onCodeChange,
                                onSubmit = viewModel::submit,
                                onBack = viewModel::backToCredentials,
                            )
                        }
                    }
                }

                Text(
                    text = "TU FUTURO EN TUS MANOS",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = InkOnPanel,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun RegisterSuccessForm(
    result: RegisterResponse,
    onDismiss: () -> Unit
) {
    val isMobile = remember { getPlatform().isMobile }
    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var openFailed by remember { mutableStateOf(false) }
    val linkStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(
            "¡Cuenta Creada!",
            if (isMobile) "Agrega tu cuenta a Google Authenticator:" else "Escanea este código con Google Authenticator:",
        )

        // El QR se genera dentro de la app: la clave nunca sale del dispositivo.
        if (result.otpauthUrl.isNotBlank()) {
            val qrPainter = rememberQrCodePainter(result.otpauthUrl) { }
            Box(
                modifier = Modifier
                    .size(if (isMobile) 180.dp else 220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = qrPainter,
                    contentDescription = "Código QR para configurar Google Authenticator",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (isMobile) {
                // En el celular no puedes escanear tu propia pantalla: se abre Authenticator directamente.
                PrimaryPillButton(
                    "Abrir en Authenticator",
                    onClick = { openFailed = runCatching { uriHandler.openUri(result.otpauthUrl) }.isFailure },
                    loading = false,
                )
                if (openFailed) {
                    ErrorBanner("No se encontró una app de autenticación. Instala Google Authenticator o usa la clave de abajo.")
                }
            }
        }

        Text(
            text = "¿No puedes escanear? En Authenticator elige \"Ingresar clave de configuración\" y escribe:",
            style = MaterialTheme.typography.bodyMedium,
            color = InkOnPanel,
            textAlign = TextAlign.Center,
        )

        PillField(
            value = result.secret2fa.chunked(4).joinToString(" "),
            onValueChange = {},
            placeholder = "",
            label = "Clave 2FA de Google Authenticator",
            textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        )

        Text(
            text = if (copied) "¡Clave copiada!" else "Copiar clave",
            style = linkStyle,
            color = InkOnPanel,
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button) {
                    clipboard.setText(AnnotatedString(result.secret2fa))
                    copied = true
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )

        Text(
            text = "Es única para tu cuenta: no la compartas.",
            style = MaterialTheme.typography.bodySmall,
            color = InkOnPanel,
            textAlign = TextAlign.Center,
        )

        PrimaryPillButton("Ya la agregué, iniciar sesión", onClick = onDismiss, loading = false)
    }
}

@Composable
private fun CredentialsForm(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSubmit: () -> Unit,
    onToggleRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    biometricLabel: String? = null,
    biometricIsFace: Boolean = false,
    biometricBusy: Boolean = false,
    onBiometric: () -> Unit = {},
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(
            title = if (state.isRegistering) "Crear cuenta" else "Iniciar sesión",
            subtitle = if (state.isRegistering) "Regístrate para comenzar." else "Entra y ten el control de tus tarjetas."
        )

        PillField(
            value = state.email,
            onValueChange = onEmailChange,
            placeholder = "Correo electrónico",
            label = "Correo electrónico",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        PillField(
            value = state.password,
            onValueChange = onPasswordChange,
            placeholder = "Contraseña",
            label = "Contraseña",
            visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            trailing = {
                Icon(
                    imageVector = if (state.passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (state.passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                    tint = Prisma.colors.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onTogglePassword).padding(8.dp),
                )
            },
        )

        state.errorMessage?.let { ErrorBanner(it) }
        state.successMessage?.let { SuccessBanner(it) }

        PrimaryPillButton(
            text = if (state.isRegistering) "Registrarse" else "Entrar",
            onClick = onSubmit,
            loading = state.isLoading
        )

        if (!state.isRegistering && biometricLabel != null) {
            BiometricPillButton(
                text = "Entrar con $biometricLabel",
                isFace = biometricIsFace,
                loading = biometricBusy,
                onClick = onBiometric,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.isRegistering) "¿Ya tienes cuenta? Inicia sesión" else "¿No tienes cuenta? Regístrate",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = InkOnPanel,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onToggleRegister)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )

            if (!state.isRegistering) {
                Text(
                    text = "¿Olvidaste tu contraseña?",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = InkOnPanel,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onForgotPassword)
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun TwoFactorForm(
    state: LoginUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header("Verifica tu identidad", "Ingresa el código de 6 dígitos de tu app de autenticación.")

        PillField(
            value = state.code,
            onValueChange = onCodeChange,
            placeholder = "000000",
            label = "Código de verificación",
            textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, letterSpacing = 8.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier.focusRequester(focus),
        )

        state.errorMessage?.let { ErrorBanner(it) }

        PrimaryPillButton("Verificar", onClick = onSubmit, loading = state.isLoading)

        Text(
            text = "Volver a iniciar sesión",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = InkOnPanel,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp, lineHeight = 34.sp),
            color = InkOnPanel,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = InkOnPanel,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.height(10.dp))
    }
}

@Composable
private fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Prisma.colors
    val centered = textStyle.copy(color = colors.onSurface, textAlign = TextAlign.Center)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = centered,
            cursorBrush = SolidColor(colors.onSurface),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = if (trailing != null) 48.dp else 20.dp)
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = centered.copy(color = colors.onSurface.copy(alpha = 0.6f)))
                    }
                    inner()
                }
            },
        )
        if (trailing != null) {
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)) { trailing() }
        }
    }
}

@Composable
private fun PrimaryPillButton(text: String, onClick: () -> Unit, loading: Boolean) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(ButtonColor)
            .clickable(enabled = !loading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = InkOnPanel, strokeWidth = 2.5.dp)
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = InkOnPanel)
        }
    }
}

@Composable
private fun BiometricPillButton(text: String, isFace: Boolean, loading: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .border(2.dp, InkOnPanel, shape)
            .clickable(enabled = !loading, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = InkOnPanel, strokeWidth = 2.5.dp)
        } else {
            Icon(
                imageVector = if (isFace) Icons.Filled.Face else Icons.Filled.Fingerprint,
                contentDescription = null,
                tint = InkOnPanel,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = InkOnPanel,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    val colors = Prisma.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).background(colors.statusCritical, CircleShape))
        Text(message, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = colors.onSurface)
    }
}

@Composable
private fun SuccessBanner(message: String) {
    val colors = Prisma.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).background(colors.statusHealthy, CircleShape))
        Text(message, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = colors.onSurface)
    }
}
