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
    /** Sesión obtenida tras el 2FA; la pantalla la entrega a la app y llama a [LoginViewModel.onSessionConsumed]. */
    val session: Verify2faResponse? = null,
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

    fun backToCredentials() {
        tempToken = null
        _state.update { it.copy(step = LoginStep.Credentials, code = "", errorMessage = null, isLoading = false) }
    }

    /**
     * La app ya recibió la sesión: se limpia todo el estado. Sin esto, al cerrar sesión y volver
     * a esta pantalla el token seguía aquí y se iniciaba sesión de nuevo automáticamente.
     */
    fun onSessionConsumed() {
        tempToken = null
        _state.value = LoginUiState()
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
                        it.copy(isLoading = false, step = LoginStep.TwoFactor, password = "", code = "")
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
                    AuthError.InvalidCode -> "El código de Google Authenticator es incorrecto o expiró."
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
