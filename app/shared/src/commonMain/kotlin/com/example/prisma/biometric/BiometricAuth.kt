package com.example.prisma.biometric

import androidx.compose.runtime.Composable

enum class BiometricKind { Fingerprint, FaceId, TouchId }

/** Nombre para mostrar al usuario: "huella digital", "Face ID" o "Touch ID". */
val BiometricKind.label: String
    get() = when (this) {
        BiometricKind.Fingerprint -> "huella digital"
        BiometricKind.FaceId -> "Face ID"
        BiometricKind.TouchId -> "Touch ID"
    }

sealed interface BiometricAvailability {
    data class Available(val kind: BiometricKind) : BiometricAvailability
    /** El dispositivo tiene sensor, pero el usuario no ha registrado huella / rostro. */
    data object NotEnrolled : BiometricAvailability
    /** Sin sensor o plataforma sin soporte (web). */
    data object Unsupported : BiometricAvailability
}

sealed interface BiometricResult<out T> {
    data class Success<T>(val value: T) : BiometricResult<T>
    /** El usuario canceló el diálogo de huella / Face ID. */
    data object Cancelled : BiometricResult<Nothing>
    /**
     * El secreto guardado ya no se puede leer: se cambiaron las huellas o el rostro registrados,
     * o se borró. Por seguridad hay que volver a activar el acceso biométrico.
     */
    data object Invalidated : BiometricResult<Nothing>
    data class Error(val message: String) : BiometricResult<Nothing>
}

data class BiometricPromptText(val title: String, val subtitle: String)

/**
 * Acceso a la biometría del dispositivo y a su almacenamiento seguro.
 *
 * - Android: BiometricPrompt + clave AES del Android Keystore que solo se puede usar tras la huella.
 * - iOS: Keychain con control de acceso "biometryCurrentSet" (Face ID / Touch ID).
 * - Web: no soportado.
 */
interface BiometricAuth {
    val availability: BiometricAvailability

    /** "android" o "ios" (lo que espera el servidor); null si no hay soporte. */
    val platformId: String?

    fun hasSecret(): Boolean

    /** Pide la biometría y guarda [secret] protegido por ella. */
    suspend fun storeSecret(secret: String, prompt: BiometricPromptText): BiometricResult<Unit>

    /** Pide la biometría y devuelve el secreto guardado. */
    suspend fun readSecret(prompt: BiometricPromptText): BiometricResult<String>

    fun clearSecret()

    /** Preferencias no secretas (id del dispositivo, correo, "no volver a preguntar"). */
    fun getPreference(key: String): String?
    fun setPreference(key: String, value: String?)
}

@Composable
expect fun rememberBiometricAuth(): BiometricAuth

/** Implementación para plataformas sin biometría. */
object UnsupportedBiometricAuth : BiometricAuth {
    override val availability: BiometricAvailability = BiometricAvailability.Unsupported
    override val platformId: String? = null
    override fun hasSecret() = false
    override suspend fun storeSecret(secret: String, prompt: BiometricPromptText) =
        BiometricResult.Error("La biometría no está disponible en este dispositivo.")
    override suspend fun readSecret(prompt: BiometricPromptText): BiometricResult<String> =
        BiometricResult.Error("La biometría no está disponible en este dispositivo.")
    override fun clearSecret() = Unit
    override fun getPreference(key: String): String? = null
    override fun setPreference(key: String, value: String?) = Unit
}
