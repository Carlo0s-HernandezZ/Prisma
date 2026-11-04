package com.example.prisma.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prisma.api.ApiRules
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
        it.copy(isRegistering = !it.isRegistering, errorMessage = null, successMessage = null, step = LoginStep.Credentials, registerResult = null)
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
            return _state.update { it.copy(errorMessage = "Escribe un correo válido.") }.let { Unit }
        }
        _state.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            repository.forgotPassword(email).fold(
                onSuccess = { msg ->
                    _state.update { it.copy(isLoading = false, resetStep = 2, successMessage = msg) }
                },
                onFailure = {
                    _state.update { it.copy(isLoading = false, errorMessage = "Error al solicitar recuperación.") }
                },
            )
        }
    }

    fun submitResetPassword() {
        val s = _state.value
        val email = s.forgotEmail.trim()
        val code = s.resetCode.trim()
        val pass = s.newPassword
        if (code.length != 6) {
            return _state.update { it.copy(errorMessage = "Ingresa el código de 6 dígitos.") }.let { Unit }
        }
        if (pass.length < ApiRules.MIN_PASSWORD_LENGTH) {
            return _state.update { it.copy(errorMessage = "La contraseña debe tener al menos ${ApiRules.MIN_PASSWORD_LENGTH} caracteres.") }.let { Unit }
        }

        _state.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            repository.resetPassword(email, code, pass).fold(
                onSuccess = { msg ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isForgotPassword = false,
                            successMessage = "¡Contraseña actualizada! Ya puedes iniciar sesión.",
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(isLoading = false, errorMessage = "Código inválido o expirado.") }
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

        _state.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            repository.register(email, s.password).fold(
                onSuccess = { response ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            registerResult = response,
                            isRegistering = false,
                            password = "",
                            successMessage = "¡Cuenta creada con éxito!",
                        )
                    }
                },
                onFailure = { e -> fail(e) },
            )
        }
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
                        it.copy(isLoading = false, step = LoginStep.TwoFactor, password = "", code = "", successMessage = "¡Código enviado a tu correo!")
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

        _state.update { it.copy(isLoading = true, errorMessage = null) }
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
                step = if (error == AuthError.SessionExpired) LoginStep.Credentials else it.step,
                code = if (error == AuthError.InvalidCode) "" else it.code,
                errorMessage = when (error) {
                    AuthError.InvalidCredentials -> "Correo o contraseña incorrectos. Revísalos e inténtalo de nuevo."
                    AuthError.InvalidCode -> "El código de verificación enviado por correo es incorrecto o expiró."
                    AuthError.SessionExpired -> "La verificación expiró. Inicia sesión de nuevo."
                    AuthError.EmailTaken -> "Ese correo ya está registrado. Inicia sesión."
                    AuthError.WeakPassword -> "La contraseña debe tener al menos ${ApiRules.MIN_PASSWORD_LENGTH} caracteres."
                    AuthError.TooManyAttempts -> "Demasiados intentos. Espera unos minutos e inténtalo de nuevo."
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
