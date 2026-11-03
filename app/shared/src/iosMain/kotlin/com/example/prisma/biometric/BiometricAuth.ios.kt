@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.example.prisma.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.LocalAuthentication.LABiometryTypeFaceID
import platform.LocalAuthentication.LABiometryTypeTouchID
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAErrorUserFallback
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.Security.SecAccessControlCreateWithFlags
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecAuthFailed
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.errSecUserCanceled
import platform.Security.kSecAccessControlBiometryCurrentSet
import platform.Security.kSecAttrAccessControl
import platform.Security.kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecUseAuthenticationContext
import platform.Security.kSecValueData
import kotlin.coroutines.resume

@Composable
actual fun rememberBiometricAuth(): BiometricAuth = remember { IosBiometricAuth() }

/**
 * El token del dispositivo se guarda en el Keychain con control de acceso
 * `biometryCurrentSet`: iOS solo lo entrega tras Face ID / Touch ID, y deja de entregarlo si
 * cambian los rostros o huellas registrados. Requiere NSFaceIDUsageDescription en Info.plist.
 */
private class IosBiometricAuth : BiometricAuth {

    private val defaults = NSUserDefaults.standardUserDefaults

    override val availability: BiometricAvailability
        get() {
            val context = LAContext()
            return memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val canEvaluate = context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error.ptr)
                when {
                    canEvaluate && context.biometryType == LABiometryTypeFaceID -> BiometricAvailability.Available(BiometricKind.FaceId)
                    canEvaluate && context.biometryType == LABiometryTypeTouchID -> BiometricAvailability.Available(BiometricKind.TouchId)
                    error.value?.code == LAErrorBiometryNotEnrolled -> BiometricAvailability.NotEnrolled
                    else -> BiometricAvailability.Unsupported
                }
            }
        }

    override val platformId: String = "ios"

    override fun hasSecret(): Boolean = defaults.boolForKey(KEY_HAS_SECRET)

    override suspend fun storeSecret(secret: String, prompt: BiometricPromptText): BiometricResult<Unit> {
        // Confirmar primero que la biometría funciona (guardar en el Keychain no muestra diálogo).
        when (val confirmed = evaluate(prompt)) {
            is BiometricResult.Success -> Unit
            is BiometricResult.Cancelled -> return confirmed
            is BiometricResult.Invalidated -> return confirmed
            is BiometricResult.Error -> return confirmed
        }

        deleteItem()
        val access = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
            kSecAccessControlBiometryCurrentSet,
            null,
        ) ?: return BiometricResult.Error("No se pudo preparar el almacenamiento seguro.")
        val data = NSString.create(string = secret).dataUsingEncoding(NSUTF8StringEncoding)
            ?: return BiometricResult.Error("No se pudo preparar el almacenamiento seguro.")

        val retained = listOf(CFBridgingRetain(SERVICE), CFBridgingRetain(ACCOUNT), CFBridgingRetain(data))
        val query = cfDictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to retained[0],
            kSecAttrAccount to retained[1],
            kSecValueData to retained[2],
            kSecAttrAccessControl to access,
        )
        val status = SecItemAdd(query, null)
        release(retained + access + query)

        return if (status == errSecSuccess) {
            defaults.setBool(true, KEY_HAS_SECRET)
            BiometricResult.Success(Unit)
        } else {
            BiometricResult.Error("No se pudo guardar el acceso de forma segura (código $status).")
        }
    }

    override suspend fun readSecret(prompt: BiometricPromptText): BiometricResult<String> {
        if (!hasSecret()) return BiometricResult.Invalidated
        val context = LAContext().apply {
            localizedReason = prompt.subtitle
            localizedCancelTitle = "Cancelar"
        }
        // SecItemCopyMatching muestra Face ID / Touch ID y bloquea el hilo: se ejecuta fuera del principal.
        return withContext(Dispatchers.IO) {
            memScoped {
                val retained = listOf(CFBridgingRetain(SERVICE), CFBridgingRetain(ACCOUNT), CFBridgingRetain(context))
                val query = cfDictionary(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to retained[0],
                    kSecAttrAccount to retained[1],
                    kSecReturnData to kCFBooleanTrue,
                    kSecMatchLimit to kSecMatchLimitOne,
                    kSecUseAuthenticationContext to retained[2],
                )
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query, result.ptr)
                release(retained + query)

                when (status) {
                    errSecSuccess -> {
                        val data = CFBridgingRelease(result.value) as? NSData
                        val value = data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }
                        if (value != null) BiometricResult.Success(value) else BiometricResult.Invalidated
                    }
                    errSecUserCanceled -> BiometricResult.Cancelled
                    // Ya no existe o cambiaron los rostros/huellas registrados (biometryCurrentSet).
                    errSecItemNotFound -> BiometricResult.Invalidated
                    errSecAuthFailed -> BiometricResult.Error("No se pudo verificar tu identidad.")
                    else -> BiometricResult.Error("No se pudo leer el acceso guardado (código $status).")
                }
            }
        }
    }

    override fun clearSecret() {
        deleteItem()
        defaults.removeObjectForKey(KEY_HAS_SECRET)
    }

    override fun getPreference(key: String): String? = defaults.stringForKey(PREF_PREFIX + key)

    override fun setPreference(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(PREF_PREFIX + key) else defaults.setObject(value, PREF_PREFIX + key)
    }

    private suspend fun evaluate(prompt: BiometricPromptText): BiometricResult<Unit> =
        suspendCancellableCoroutine { cont ->
            val context = LAContext().apply { localizedCancelTitle = "Cancelar" }
            context.evaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, prompt.subtitle) { success, error ->
                if (!cont.isActive) return@evaluatePolicy
                cont.resume(
                    when {
                        success -> BiometricResult.Success(Unit)
                        error?.code in CANCEL_CODES -> BiometricResult.Cancelled
                        else -> BiometricResult.Error(error?.localizedDescription ?: "No se pudo verificar tu identidad.")
                    }
                )
            }
            cont.invokeOnCancellation { context.invalidate() }
        }

    private fun deleteItem() {
        val retained = listOf(CFBridgingRetain(SERVICE), CFBridgingRetain(ACCOUNT))
        val query = cfDictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to retained[0],
            kSecAttrAccount to retained[1],
        )
        SecItemDelete(query)
        release(retained + query)
    }

    private fun cfDictionary(vararg pairs: Pair<CFStringRef?, CFTypeRef?>): CFMutableDictionaryRef? {
        val dict = CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            pairs.size.convert(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
        pairs.forEach { (key, value) -> CFDictionaryAddValue(dict, key, value) }
        return dict
    }

    private fun release(refs: List<CFTypeRef?>) = refs.forEach { it?.let(::CFRelease) }

    private companion object {
        const val SERVICE = "com.example.prisma.biometric"
        const val ACCOUNT = "device_token"
        const val KEY_HAS_SECRET = "prisma_biometric_has_secret"
        const val PREF_PREFIX = "prisma_biometric_"
        val CANCEL_CODES = listOf(LAErrorUserCancel, LAErrorSystemCancel, LAErrorAppCancel, LAErrorUserFallback)
    }
}
