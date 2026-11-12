package com.example.prisma.ui

import com.example.prisma.termsPageUrl
import com.example.prisma.getPlatform
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.runtime.rememberCoroutineScope
import com.example.prisma.api.ApiRules
import com.example.prisma.api.Consent
import com.example.prisma.api.RegisterResponse
import com.example.prisma.api.Verify2faResponse
import com.example.prisma.biometric.BiometricController
import com.example.prisma.biometric.BiometricKind
import com.example.prisma.biometric.BiometricUnlockResult
import kotlinx.coroutines.launch
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
    /** null (por defecto): abre el formulario de recuperación de esta misma pantalla. */
    onForgotPassword: (() -> Unit)? = null,
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
                        "Entra con tu contraseña y el código que te enviaremos por correo.",
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
    val isWeb = remember { getPlatform().isWeb }

    // Formulario (registro exitoso, credenciales o código 2FA). Igual en móvil y web.
    val formContent: @Composable () -> Unit = {
        if (state.registerResult != null) {
            RegisterSuccessForm(
                result = state.registerResult!!,
                onDismiss = viewModel::dismissRegisterResult
            )
        } else if (state.isForgotPassword) {
            ForgotPasswordForm(
                state = state,
                onEmailChange = viewModel::onForgotEmailChange,
                onCodeChange = viewModel::onResetCodeChange,
                onPasswordChange = viewModel::onNewPasswordChange,
                onTogglePassword = viewModel::togglePasswordVisibility,
                onSubmitEmail = viewModel::submitForgotPassword,
                onSubmitReset = viewModel::submitResetPassword,
                onResend = viewModel::backToRequestCode,
                onBack = viewModel::toggleForgotPassword,
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
                        onForgotPassword = onForgotPassword ?: viewModel::toggleForgotPassword,
                        biometricLabel = if (biometricAvailable) biometric.label else null,
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
    }

    // Ventana emergente de Términos y Condiciones: aparece al pulsar "Registrarse".
    if (state.showConsentDialog) {
        ConsentDialog(
            accepted = state.consents,
            showErrors = state.showConsentErrors,
            errorMessage = state.errorMessage,
            loading = state.isLoading,
            onToggle = viewModel::toggleConsent,
            onConfirm = viewModel::confirmRegister,
            onDismiss = viewModel::dismissConsentDialog,
        )
    }

    if (isDesktopLayout) {
        // Web en pantalla ancha: panel de marca a la izquierda y formulario a la derecha.
        // Android e iOS nunca entran aquí (isDesktopLayout solo es true en la web).
        Column(modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            DesktopBrandPanel(Modifier.weight(0.48f).fillMaxHeight())
            BoxWithConstraints(Modifier.weight(0.52f).fillMaxHeight().background(PanelColor)) {
                val panelHeight = maxHeight
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        Modifier
                            .heightIn(min = panelHeight)
                            .widthIn(max = 440.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp, vertical = 48.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        formContent()
                    }
                }
            }
        }
        WebLegalFooter()
        }
        return
    }

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
                formContent()

                Text(
                    text = "TU FUTURO EN TUS MANOS",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = InkOnPanel,
                    modifier = Modifier.padding(top = 24.dp),
                )
                if (isWeb) {
                    Box(Modifier.padding(top = 12.dp)) { LegalLinks(color = InkOnPanel, fontSize = 12.sp, centered = true) }
                }
            }
        }
    }
}

@Composable
private fun RegisterSuccessForm(
    result: RegisterResponse,
    onDismiss: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(
            "¡Cuenta creada!",
            "Cada vez que inicies sesión te enviaremos un código de 6 dígitos a tu correo para verificar que eres tú.",
        )
        PrimaryPillButton("Iniciar sesión", onClick = onDismiss, loading = false)
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

        if (!state.showConsentDialog) state.errorMessage?.let { ErrorBanner(it) }
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

/**
 * Recuperación de contraseña en dos pasos:
 * 1) correo → el servidor envía un código de 6 dígitos;
 * 2) código + contraseña nueva.
 */
@Composable
private fun ForgotPasswordForm(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSubmitEmail: () -> Unit,
    onSubmitReset: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(
            title = "Recuperar contraseña",
            subtitle = if (state.resetStep == 1) {
                "Escribe tu correo y te enviaremos un código para crear una contraseña nueva."
            } else {
                "Escribe el código de 6 dígitos que enviamos a ${state.forgotEmail.trim()} y tu nueva contraseña."
            },
        )

        if (state.resetStep == 1) {
            PillField(
                value = state.forgotEmail,
                onValueChange = onEmailChange,
                placeholder = "Correo electrónico",
                label = "Correo electrónico",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmitEmail() }),
            )
            state.errorMessage?.let { ErrorBanner(it) }
            PrimaryPillButton(text = "Enviar código", onClick = onSubmitEmail, loading = state.isLoading)
        } else {
            PillField(
                value = state.resetCode,
                onValueChange = onCodeChange,
                placeholder = "000000",
                label = "Código de recuperación",
                textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, letterSpacing = 8.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            PillField(
                value = state.newPassword,
                onValueChange = onPasswordChange,
                placeholder = "Nueva contraseña (mín. ${ApiRules.MIN_PASSWORD_LENGTH})",
                label = "Nueva contraseña",
                visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmitReset() }),
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
            PrimaryPillButton(text = "Cambiar contraseña", onClick = onSubmitReset, loading = state.isLoading)
            LinkText("¿No te llegó? Reenviar código", onResend)
        }

        LinkText("Volver a iniciar sesión", onBack)
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        color = InkOnPanel,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    )
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
        Header(
            "Verifica tu identidad",
            "Ingresa el código de 6 dígitos que enviamos a ${state.email.trim()}. Revisa también la carpeta de spam.",
        )

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

/**
 * Ventana emergente de Términos y Condiciones / Aviso de Privacidad al crear la cuenta.
 * Solo las casillas obligatorias son necesarias para registrarse; las opcionales quedan a elección del usuario.
 * Si se intenta continuar sin las obligatorias, se resaltan las que faltan.
 */
@Composable
private fun ConsentDialog(
    accepted: Set<Consent>,
    showErrors: Boolean,
    errorMessage: String?,
    loading: Boolean,
    onToggle: (Consent) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Prisma.colors
    val desktop = isDesktopLayout
    val requiredDone = Consent.requiredOnes.count { it in accepted }
    val requiredTotal = Consent.requiredOnes.size
    val canContinue = requiredDone == requiredTotal

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = !loading,
            dismissOnBackPress = !loading,
            usePlatformDefaultWidth = !desktop,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.surface,
            contentColor = colors.onSurface,
            shadowElevation = 12.dp,
            modifier = if (desktop) Modifier.widthIn(max = 560.dp).fillMaxWidth() else Modifier.fillMaxWidth().widthIn(max = 460.dp),
        ) {
            Column(Modifier.padding(if (desktop) 28.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandLabel("Crear cuenta", color = PrismaPalette.NaranjaAmbar)
                Text(
                    "Términos y Condiciones",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                )
                Text(
                    "Para crear tu cuenta acepta las casillas obligatorias. Las opcionales puedes dejarlas sin marcar.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = colors.onSurface.copy(alpha = 0.8f),
                )
                // Documento completo (página pública): se abre en el navegador.
                LegalLinks(color = PrismaPalette.NaranjaAmbar, fontSize = 13.sp, leading = "Léelos aquí: ")

                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.background)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ConsentGroupTitle("Obligatorias ($requiredDone de $requiredTotal)")
                    Consent.requiredOnes.forEach { consent ->
                        ConsentRow(consent, checked = consent in accepted, highlight = showErrors && consent !in accepted, onToggle = onToggle)
                    }
                    Box(Modifier.height(6.dp))
                    ConsentGroupTitle("Opcionales (no afectan el servicio)")
                    Consent.optionalOnes.forEach { consent ->
                        ConsentRow(consent, checked = consent in accepted, highlight = false, onToggle = onToggle)
                    }
                }

                when {
                    showErrors && !canContinue ->
                        ErrorBanner("Marca las ${requiredTotal - requiredDone} casillas obligatorias que faltan para continuar.")
                    errorMessage != null -> ErrorBanner(errorMessage)
                }

                ConsentConfirmButton(
                    text = "Aceptar y crear cuenta",
                    enabledLook = canContinue,
                    loading = loading,
                    onClick = onConfirm,
                )
                Text(
                    text = "Cancelar",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = !loading, role = Role.Button, onClick = onDismiss)
                        .padding(vertical = 14.dp),
                )
            }
        }
    }
}

/** Botón principal de la ventana: se ve atenuado hasta marcar las obligatorias (al pulsarlo resalta las que faltan). */
@Composable
private fun ConsentConfirmButton(text: String, enabledLook: Boolean, loading: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(if (enabledLook) ButtonColor else ButtonColor.copy(alpha = 0.45f))
            .clickable(enabled = !loading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = InkOnPanel, strokeWidth = 2.5.dp)
        } else {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabledLook) InkOnPanel else InkOnPanel.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ConsentGroupTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold),
        color = Prisma.colors.onSurface.copy(alpha = 0.75f),
        modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun ConsentRow(consent: Consent, checked: Boolean, highlight: Boolean, onToggle: (Consent) -> Unit) {
    val ink = Prisma.colors.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlight) PrismaPalette.NaranjaPrisma.copy(alpha = 0.16f) else Color.Transparent)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(consent) })
            .padding(end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null, // el clic lo maneja toda la fila (área táctil más grande)
            colors = CheckboxDefaults.colors(
                checkedColor = PrismaPalette.NaranjaPrisma,
                uncheckedColor = if (highlight) PrismaPalette.NaranjaAmbar else ink.copy(alpha = 0.7f),
                checkmarkColor = PrismaPalette.Grafito,
            ),
            modifier = Modifier.padding(12.dp).size(20.dp),
        )
        Text(
            consent.text + if (highlight) "  (obligatoria)" else "",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = ink,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * Enlaces a la página pública de Términos y Condiciones (Parte A) y Aviso de Privacidad (Parte B).
 * En la web abre una pestaña nueva; en Android/iOS abre el navegador del teléfono.
 */
@Composable
private fun LegalLinks(color: Color, fontSize: TextUnit, leading: String = "", centered: Boolean = false) {
    val uriHandler = LocalUriHandler.current
    val linkStyle = SpanStyle(color = color, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
    val text = buildAnnotatedString {
        if (leading.isNotEmpty()) withStyle(SpanStyle(color = Prisma.colors.onSurface.copy(alpha = 0.8f))) { append(leading) }
        withLink(LinkAnnotation.Clickable("terminos", TextLinkStyles(linkStyle)) { openSafely(uriHandler, termsPageUrl("parte-a")) }) {
            append("Términos y Condiciones")
        }
        withStyle(SpanStyle(color = color)) { append("  ·  ") }
        withLink(LinkAnnotation.Clickable("privacidad", TextLinkStyles(linkStyle)) { openSafely(uriHandler, termsPageUrl("parte-b")) }) {
            append("Aviso de Privacidad")
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = fontSize, lineHeight = fontSize * 1.5f),
        color = color,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.semantics { contentDescription = "Leer Términos y Condiciones y Aviso de Privacidad" },
    )
}

/** Abrir el navegador puede fallar (sin navegador instalado, etc.): no debe cerrar la app. */
private fun openSafely(uriHandler: UriHandler, url: String) {
    runCatching { uriHandler.openUri(url) }
}

/** Pie de la portada web: empresa, lema y enlaces legales públicos. */
@Composable
private fun WebLegalFooter() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(PrismaPalette.Grafito)
            .padding(horizontal = 40.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "© 2026 TDSoft Company · PRISMA",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = PrismaPalette.Crema.copy(alpha = 0.75f),
        )
        Text(
            "TU FUTURO EN TUS MANOS",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 2.sp),
            color = PrismaPalette.Dorado,
        )
        LegalLinks(color = PrismaPalette.Crema, fontSize = 12.sp)
    }
}
