package com.example.prisma.biometric

import com.example.prisma.storage.SecurePrefs
import android.content.Context
import android.content.ContextWrapper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

@Composable
actual fun rememberBiometricAuth(): BiometricAuth {
    val context = LocalContext.current
    return remember(context) {
        val activity = context.findFragmentActivity()
        if (activity == null) UnsupportedBiometricAuth else AndroidBiometricAuth(activity)
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

/**
 * El token del dispositivo se cifra con AES-256-GCM usando una clave del Android Keystore que:
 * - solo se puede usar inmediatamente después de autenticar con huella (BIOMETRIC_STRONG), y
 * - se invalida si el usuario agrega o cambia huellas (setInvalidatedByBiometricEnrollment).
 * Así, aunque alguien copie los datos de la app, no puede descifrar el token sin la huella.
 */
private class AndroidBiometricAuth(private val activity: FragmentActivity) : BiometricAuth {

    // EncryptedSharedPreferences (AES-256, llave en Android Keystore, MODE_PRIVATE). Migra el archivo viejo sin cifrar.
    private val prefs = SecurePrefs.open(activity, legacyName = "prisma_biometric")

    override val availability: BiometricAvailability
        get() = when (BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available(BiometricKind.Fingerprint)
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NotEnrolled
            else -> BiometricAvailability.Unsupported
        }

    override val platformId: String = "android"

    override fun hasSecret(): Boolean = prefs.contains(KEY_CIPHERTEXT) && prefs.contains(KEY_IV)

    override suspend fun storeSecret(secret: String, prompt: BiometricPromptText): BiometricResult<Unit> {
        val cipher = try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, createKey()) }
        } catch (e: Exception) {
            return BiometricResult.Error("No se pudo preparar el almacenamiento seguro.")
        }
        return when (val auth = authenticate(prompt, cipher)) {
            is BiometricResult.Success -> try {
                val encrypted = auth.value.doFinal(secret.encodeToByteArray())
                prefs.edit()
                    .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(KEY_IV, Base64.encodeToString(auth.value.iv, Base64.NO_WRAP))
                    .apply()
                BiometricResult.Success(Unit)
            } catch (e: Exception) {
                BiometricResult.Error("No se pudo guardar el acceso de forma segura.")
            }
            is BiometricResult.Cancelled -> auth
            is BiometricResult.Invalidated -> auth
            is BiometricResult.Error -> auth
        }
    }

    override suspend fun readSecret(prompt: BiometricPromptText): BiometricResult<String> {
        val ciphertext = prefs.getString(KEY_CIPHERTEXT, null) ?: return BiometricResult.Invalidated
        val iv = prefs.getString(KEY_IV, null) ?: return BiometricResult.Invalidated
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: return BiometricResult.Invalidated

        val cipher = try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Se agregaron o eliminaron huellas: la clave ya no sirve.
            return BiometricResult.Invalidated
        } catch (e: Exception) {
            return BiometricResult.Error("No se pudo leer el acceso guardado.")
        }

        return when (val auth = authenticate(prompt, cipher)) {
            is BiometricResult.Success -> try {
                BiometricResult.Success(auth.value.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)).decodeToString())
            } catch (e: Exception) {
                BiometricResult.Invalidated
            }
            is BiometricResult.Cancelled -> auth
            is BiometricResult.Invalidated -> auth
            is BiometricResult.Error -> auth
        }
    }

    override fun clearSecret() {
        prefs.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).apply()
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    override fun getPreference(key: String): String? = prefs.getString(PREF_PREFIX + key, null)

    override fun setPreference(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(PREF_PREFIX + key) else putString(PREF_PREFIX + key, value) }.apply()
    }

    /** Muestra el diálogo de huella y devuelve el Cipher ya autorizado. */
    private suspend fun authenticate(prompt: BiometricPromptText, cipher: Cipher): BiometricResult<Cipher> =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val authorized = result.cryptoObject?.cipher
                        if (cont.isActive) {
                            cont.resume(
                                if (authorized != null) BiometricResult.Success(authorized)
                                else BiometricResult.Error("No se pudo verificar la huella.")
                            )
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!cont.isActive) return
                        cont.resume(
                            when (errorCode) {
                                BiometricPrompt.ERROR_USER_CANCELED,
                                BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                                BiometricPrompt.ERROR_CANCELED -> BiometricResult.Cancelled
                                else -> BiometricResult.Error(errString.toString())
                            }
                        )
                    }
                    // onAuthenticationFailed (huella no reconocida): el diálogo sigue abierto para reintentar.
                }

                val biometricPrompt = BiometricPrompt(activity, activity.mainExecutor, callback)
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(prompt.title)
                    .setSubtitle(prompt.subtitle)
                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .setNegativeButtonText("Cancelar")
                    .build()
                biometricPrompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
                cont.invokeOnCancellation { biometricPrompt.cancelAuthentication() }
            }
        }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** Crea una clave nueva (reemplaza la anterior) que exige huella en cada uso. */
    private fun createKey(): SecretKey {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "prisma_biometric_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_CIPHERTEXT = "secret_ciphertext"
        const val KEY_IV = "secret_iv"
        const val PREF_PREFIX = "pref_"
    }
}
