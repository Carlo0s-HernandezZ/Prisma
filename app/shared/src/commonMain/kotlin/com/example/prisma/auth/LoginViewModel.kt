package com.example.prisma.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prisma.api.ApiRules
import com.example.prisma.api.Consent
import com.example.prisma.api.RegisterResponse
import com.example.prisma.api.Verify2faResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginStep { Credentials, TwoFactor }

data class LoginUiState(
    val step: LoginStep = LoginStep.Credentials,
    val email: String = "",
    val password: String = "",
    val code: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isRegistering: Boolean = false,
    /** Casillas de Términos / Aviso de Privacidad marcadas al crear la cuenta. */
    val consents: Set<Consent> = emptySet(),
    /** true tras intentar registrarse sin las obligatorias: se resaltan las que faltan. */
    val showConsentErrors: Boolean = false,
    /** Ventana emergente de Términos y Condiciones abierta (aparece al pulsar "Registrarse"). */
    val showConsentDialog: Boolean = false,
    val registerResult: RegisterResponse? = null,
    val session: Verify2faResponse? = null,
    // Recuperación de contraseña
    val isForgotPassword: Boolean = false,
    val forgotEmail: String = "",
    val resetCode: String = "",
    val newPassword: String = "",
    val resetStep: Int = 1, // 1: pedir correo, 2: ingresar código y nueva contraseña
)

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var tempToken: String? = null

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, errorMessage = null, successMessage = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, errorMessage = null, successMessage = null) }

    fun onCodeChange(value: String) =
        _state.update { it.copy(code = value.filter(Char::isDigit).take(ApiRules.TOTP_CODE_LENGTH), errorMessage = null) }

    fun togglePasswordVisibility() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun toggleRegisterMode() = _state.update {
        it.copy(
            isRegistering = !it.isRegistering, errorMessage = null, successMessage = null, step = LoginStep.Credentials,
            registerResult = null, consents = emptySet(), showConsentErrors = false, showConsentDialog = false,
        )
    }

    fun toggleConsent(consent: Consent) = _state.update {
        val updated = if (consent in it.consents) it.consents - consent else it.consents + consent
        val allRequired = updated.containsAll(Consent.requiredOnes)
        it.copy(
            consents = updated,
            showConsentErrors = it.showConsentErrors && !allRequired,
        )
    }

    /** Cierra la ventana de Términos sin registrar (las casillas marcadas se conservan). */
    fun dismissConsentDialog() = _state.update {
        if (it.isLoading) it else it.copy(showConsentDialog = false, showConsentErrors = false)
    }

    /**
     * Botón "Aceptar y crear cuenta" de la ventana de Términos.
     * Solo exige las casillas obligatorias; las opcionales se envían tal como el usuario las dejó.
     */
    fun confirmRegister() {
        val s = _state.value
        if (s.isLoading) return
        if (!s.consents.containsAll(Consent.requiredOnes)) {
            _state.update { it.copy(showConsentErrors = true) }
            return
        }
        _state.update { it.copy(isLoading = true, errorMessage = null, successMessage = null, showConsentErrors = false) }
        viewModelScope.launch {
            repository.register(s.email.trim(), s.password, s.consents).fold(
                onSuccess = { response ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            registerResult = response,
                            isRegistering = false,
                            password = "",
                            consents = emptySet(),
                            showConsentErrors = false,
                            showConsentDialog = false,
                            successMessage = "¡Cuenta creada con éxito!",
                        )
                    }
                },
                onFailure = { e -> fail(e) },
            )
        }
    }

    fun dismissRegisterResult() {
        _state.update { it.copy(registerResult = null, isRegistering = false) }
    }

    fun toggleForgotPassword() = _state.update {
        it.copy(
            isForgotPassword = !it.isForgotPassword,
            forgotEmail = it.email,
            resetCode = "",
            newPassword = "",
            resetStep = 1,
            errorMessage = null,
            successMessage = null,
        )
    }

    fun onForgotEmailChange(value: String) = _state.update { it.copy(forgotEmail = value, errorMessage = null, successMessage = null) }
    fun onResetCodeChange(value: String) = _state.update { it.copy(resetCode = value.filter(Char::isDigit).take(6), errorMessage = null, successMessage = null) }
    fun onNewPasswordChange(value: String) = _state.update { it.copy(newPassword = value, errorMessage = null, successMessage = null) }

    fun submitForgotPassword() {
        val email = _state.value.forgotEmail.trim()
        if (!EMAIL_REGEX.matches(email)) {
            return _state.update { it.copy(errorMessage = "Escribe un correo válido.") }
        }
        _state.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            repository.forgotPassword(email).fold(
                onSuccess = { msg ->
                    _state.update { it.copy(isLoading = false, resetStep = 2, successMessage = msg) }
                },
                onFailure = { e ->
                    val message = when ((e as? AuthException)?.error) {
                        AuthError.Network -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
                        AuthError.TooManyAttempts -> "Ya pediste varios códigos. Espera 15 minutos o usa el último que te llegó."
                        else -> "No se pudo enviar el código. Inténtalo de nuevo."
                    }
                    _state.update { it.copy(isLoading = false, errorMessage = message) }
                },
            )
        }
    }

    /** Paso 2 → paso 1: para pedir otro código o corregir el correo. */
    fun backToRequestCode() = _state.update {
        if (it.isLoading) it else it.copy(resetStep = 1, resetCode = "", newPassword = "", errorMessage = null, successMessage = null)
    }

    fun submitResetPassword() {
        val s = _state.value
        val email = s.forgotEmail.trim()
        val code = s.resetCode.trim()
        val pass = s.newPassword
        if (code.length != 6) {
            return _state.update { it.copy(errorMessage = "Ingresa el código de 6 dígitos.") }
        }
        if (pass.length < ApiRules.MIN_PASSWORD_LENGTH) {
            return _state.update { it.copy(errorMessage = "La contraseña debe tener al menos ${ApiRules.MIN_PASSWORD_LENGTH} caracteres.") }
        }

        _state.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            repository.resetPassword(email, code, pass).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isForgotPassword = false,
                            email = email, // listo para entrar con la contraseña nueva
                            password = "",
                            resetCode = "",
                            newPassword = "",
                            resetStep = 1,
                            errorMessage = null,
                            successMessage = "¡Contraseña actualizada! Ya puedes iniciar sesión.",
                        )
                    }
                },
                onFailure = { e ->
                    when ((e as? AuthException)?.error) {
                        // Código caducado o sin intentos: se vuelve al paso 1 para pedir otro.
                        AuthError.SessionExpired -> _state.update {
                            it.copy(isLoading = false, resetStep = 1, resetCode = "", successMessage = null,
                                errorMessage = "El código expiró o se agotaron los intentos. Solicita uno nuevo.")
                        }
                        else -> _state.update {
                            it.copy(isLoading = false, errorMessage = when ((e as? AuthException)?.error) {
                                AuthError.InvalidCode -> "Código incorrecto. Revisa el último correo que te enviamos."
                                AuthError.WeakPassword -> "La contraseña debe tener al menos ${ApiRules.MIN_PASSWORD_LENGTH} caracteres."
                                AuthError.Network -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
                                else -> "No se pudo cambiar la contraseña. Inténtalo de nuevo."
                            })
                        }
                    }
                },
            )
        }
    }

    fun backToCredentials() {
        tempToken = null
        _state.update { it.copy(step = LoginStep.Credentials, code = "", errorMessage = null, isLoading = false) }
    }

    fun onSessionConsumed() {
        tempToken = null
        _state.value = LoginUiState()
    }

    fun showNotice(message: String, email: String? = null) {
        tempToken = null
        _state.update {
            it.copy(
                step = LoginStep.Credentials,
                isRegistering = false,
                registerResult = null,
                email = email ?: it.email,
                code = "",
                isLoading = false,
                errorMessage = message,
                successMessage = null,
            )
        }
    }

    fun submit() = when (_state.value.step) {
        LoginStep.Credentials -> if (_state.value.isRegistering) register() else submitCredentials()
        LoginStep.TwoFactor -> submitCode()
    }

    private fun register() {
        val s = _state.value
        if (s.isLoading) return
        val email = s.email.trim()
        if (!EMAIL_REGEX.matches(email)) return showError("Escribe un correo válido.")
        if (s.password.length < ApiRules.MIN_PASSWORD_LENGTH) {
            return showError("La contraseña debe tener al menos ${ApiRules.MIN_PASSWORD_LENGTH} caracteres.")
        }

        // Correo y contraseña válidos: se abre la ventana de Términos para aceptarlos.
        _state.update { it.copy(showConsentDialog = true, errorMessage = null, successMessage = null) }
    }

    private fun submitCredentials() {
        val s = _state.value
        if (s.isLoading) return
        val email = s.email.trim()
        if (!EMAIL_REGEX.matches(email)) {
            return showError("Escribe un correo válido, por ejemplo nombre@correo.com.")
        }
        if (s.password.isEmpty()) return showError("Escribe tu contraseña.")

        _state.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            repository.login(email, s.password).fold(
                onSuccess = { temp ->
                    tempToken = temp
                    _state.update {
                        it.copy(isLoading = false, step = LoginStep.TwoFactor, password = "", code = "", successMessage = "Te enviamos un código a $email.")
                    }
                },
                onFailure = { e -> fail(e) },
            )
        }
    }

    private fun submitCode() {
        val s = _state.value
        if (s.isLoading) return
        val temp = tempToken ?: return backToCredentials()
        if (s.code.length != ApiRules.TOTP_CODE_LENGTH) return showError("Ingresa los 6 dígitos del código.")

        _state.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            repository.verifyTwoFactor(temp, s.code).fold(
                onSuccess = { session ->
                    tempToken = null
                    _state.update { it.copy(isLoading = false, session = session) }
                },
                onFailure = { e -> fail(e) },
            )
        }
    }

    private fun showError(message: String) = _state.update { it.copy(errorMessage = message) }

    private fun fail(e: Throwable) {
        val error = (e as? AuthException)?.error ?: AuthError.Unknown
        if (error == AuthError.SessionExpired) tempToken = null
        _state.update {
            it.copy(
                isLoading = false,
                // Errores del registro (p. ej. correo ya usado) se muestran en el formulario, salvo si faltan casillas.
                showConsentDialog = it.showConsentDialog && error == AuthError.ConsentRequired,
                showConsentErrors = it.showConsentDialog && error == AuthError.ConsentRequired,
                step = if (error == AuthError.SessionExpired) LoginStep.Credentials else it.step,
                code = if (error == AuthError.InvalidCode) "" else it.code,
                successMessage = null,
                errorMessage = when (error) {
                    AuthError.InvalidCredentials -> "Correo o contraseña incorrectos. Revísalos e inténtalo de nuevo."
                    AuthError.InvalidCode -> "Código incorrecto. Revisa el último correo que te enviamos e inténtalo de nuevo."
                    AuthError.SessionExpired -> "El código expiró o se agotaron los intentos. Inicia sesión de nuevo para recibir uno nuevo."
                    AuthError.EmailTaken -> "Ese correo ya está registrado. Inicia sesión."
                    AuthError.WeakPassword -> "La contraseña debe tener al menos ${ApiRules.MIN_PASSWORD_LENGTH} caracteres."
                    AuthError.TooManyAttempts -> "Demasiados intentos. Espera unos minutos e inténtalo de nuevo."
                    AuthError.EmailFailed -> "No pudimos enviar el código a tu correo. Inténtalo de nuevo en unos minutos."
                    AuthError.ConsentRequired -> "Para crear tu cuenta marca las casillas obligatorias."
                    AuthError.Network -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
                    AuthError.Unknown -> "Algo salió mal. Inténtalo de nuevo."
                },
            )
        }
    }

    private companion object {
        val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
