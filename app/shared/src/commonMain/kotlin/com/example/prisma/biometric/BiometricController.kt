package com.example.prisma.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.prisma.api.ApiRules
import com.example.prisma.api.Verify2faResponse

sealed interface BiometricUnlockResult {
    data class Success(val session: Verify2faResponse) : BiometricUnlockResult
    /** Pasaron 24 h desde el último 2FA: hay que entrar con contraseña + código (el acceso sigue activo). */
    data class TwoFactorRequired(val email: String) : BiometricUnlockResult
    /** El acceso biométrico dejó de ser válido y se desactivó en este dispositivo. */
    data class Disabled(val message: String) : BiometricUnlockResult
    data object Cancelled : BiometricUnlockResult
    data class Error(val message: String) : BiometricUnlockResult
}

sealed interface BiometricEnableResult {
    data object Enabled : BiometricEnableResult
    data object Cancelled : BiometricEnableResult
    data class Error(val message: String) : BiometricEnableResult
}

/**
 * Orquesta el acceso biométrico: une la biometría del teléfono ([BiometricAuth]) con el
 * servidor ([BiometricRepository]).
 *
 * Reglas:
 * - Solo se ofrece tras un login completo con 2FA, en Android/iOS con biometría configurada.
 * - Con el acceso activo, la app se desbloquea con huella / Face ID durante 24 h desde el
 *   último código 2FA; el servidor es quien hace cumplir ese límite.
 * - Cada login completo con 2FA renueva la ventana sin volver a pedir la biometría.
 */
@Stable
class BiometricController(
    private val auth: BiometricAuth,
    private val repository: BiometricRepository = BiometricRepository(),
) {
    /** Correo de la cuenta con acceso biométrico activo en este dispositivo, o null. */
    var enrolledEmail: String? by mutableStateOf(loadEnrolledEmail())
        private set

    val kind: BiometricKind?
        get() = (auth.availability as? BiometricAvailability.Available)?.kind

    /** El dispositivo tiene biometría configurada y la plataforma la soporta. */
    val isSupported: Boolean
        get() = kind != null && auth.platformId != null

    val label: String get() = kind?.label ?: "biometría"

    private fun loadEnrolledEmail(): String? =
        if (auth.hasSecret() && auth.getPreference(KEY_DEVICE_ID) != null) auth.getPreference(KEY_EMAIL) else null

    /**
     * Llamar después de cada login con contraseña + 2FA.
     * Devuelve true si se debe preguntar al usuario si quiere activar la biometría.
     */
    suspend fun onTwoFactorLogin(accessToken: String, email: String): Boolean {
        if (!isSupported) return false
        val deviceId = auth.getPreference(KEY_DEVICE_ID)
        if (enrolledEmail.equals(email, ignoreCase = true) && deviceId != null) {
            repository.renew(accessToken, deviceId).onFailure { e ->
                if ((e as? BiometricApiException)?.error == BiometricApiError.Revoked) clearLocal()
            }
            if (enrolledEmail != null) return false
        }
        return auth.getPreference(KEY_DECLINED_FOR) != email.lowercase()
    }

    /** El usuario eligió "Ahora no": no se vuelve a preguntar para esta cuenta (puede activarlo en Perfil). */
    fun decline(email: String) = auth.setPreference(KEY_DECLINED_FOR, email.lowercase())

    suspend fun enable(accessToken: String, email: String): BiometricEnableResult {
        val platform = auth.platformId ?: return BiometricEnableResult.Error("La biometría no está disponible en este dispositivo.")

        // Si había otra cuenta (o un registro viejo) en este dispositivo, se reemplaza.
        clearLocal()

        val enrolled = repository.enroll(accessToken, platform).getOrElse { e ->
            return BiometricEnableResult.Error(
                when ((e as? BiometricApiException)?.error) {
                    BiometricApiError.Network -> "Sin conexión con el servidor. Revisa tu internet e inténtalo de nuevo."
                    BiometricApiError.Unauthorized -> "Tu sesión expiró. Vuelve a iniciar sesión para activar el acceso con $label."
                    // 404/500: el servidor no tiene la función (versión anterior) o falló.
                    else -> "El servidor no pudo activar el acceso con $label. Verifica que el servidor esté actualizado y reiniciado."
                }
            )
        }

        val prompt = BiometricPromptText(
            title = "Activar acceso con $label",
            subtitle = "Confirma para entrar a Prisma sin escribir tu contraseña",
        )
        return when (val stored = auth.storeSecret(enrolled.deviceToken, prompt)) {
            is BiometricResult.Success -> {
                auth.setPreference(KEY_DEVICE_ID, enrolled.deviceId)
                auth.setPreference(KEY_EMAIL, email)
                auth.setPreference(KEY_DECLINED_FOR, null)
                enrolledEmail = email
                BiometricEnableResult.Enabled
            }
            else -> {
                // No quedó guardado en el teléfono: se elimina también del servidor.
                repository.revoke(accessToken, enrolled.deviceId)
                auth.clearSecret()
                when (stored) {
                    BiometricResult.Cancelled -> BiometricEnableResult.Cancelled
                    is BiometricResult.Error -> BiometricEnableResult.Error(stored.message)
                    else -> BiometricEnableResult.Error("No se pudo activar el acceso con $label.")
                }
            }
        }
    }

    suspend fun disable(accessToken: String) {
        auth.getPreference(KEY_DEVICE_ID)?.let { repository.revoke(accessToken, it) }
        clearLocal()
    }

    suspend fun unlock(): BiometricUnlockResult {
        val email = enrolledEmail ?: return BiometricUnlockResult.Disabled("El acceso con $label no está activo.")
        val prompt = BiometricPromptText(title = "Entrar a Prisma", subtitle = "Usa tu $label para continuar")

        val deviceToken = when (val read = auth.readSecret(prompt)) {
            is BiometricResult.Success -> read.value
            BiometricResult.Cancelled -> return BiometricUnlockResult.Cancelled
            BiometricResult.Invalidated -> {
                clearLocal()
                return BiometricUnlockResult.Disabled(
                    "Cambiaron los datos biométricos del dispositivo. Por seguridad, entra con tu contraseña y vuelve a activar el acceso con $label."
                )
            }
            is BiometricResult.Error -> return BiometricUnlockResult.Error(read.message)
        }

        return repository.login(deviceToken).fold(
            onSuccess = { BiometricUnlockResult.Success(it) },
            onFailure = { e ->
                when ((e as? BiometricApiException)?.error) {
                    BiometricApiError.TwoFactorRequired -> BiometricUnlockResult.TwoFactorRequired(email)
                    BiometricApiError.Revoked -> {
                        clearLocal()
                        BiometricUnlockResult.Disabled("El acceso con $label se desactivó. Inicia sesión con tu contraseña.")
                    }
                    BiometricApiError.Network -> BiometricUnlockResult.Error("Sin conexión con el servidor. Inténtalo de nuevo.")
                    else -> BiometricUnlockResult.Error("No se pudo entrar con $label. Inténtalo de nuevo.")
                }
            },
        )
    }

    private fun clearLocal() {
        auth.clearSecret()
        auth.setPreference(KEY_DEVICE_ID, null)
        auth.setPreference(KEY_EMAIL, null)
        enrolledEmail = null
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_DECLINED_FOR = "declined_for"

    }
}

@Composable
fun rememberBiometricController(): BiometricController {
    val auth = rememberBiometricAuth()
    return remember(auth) { BiometricController(auth) }
}
