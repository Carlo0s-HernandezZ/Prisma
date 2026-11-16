package com.example.prisma.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Preferencias locales cifradas (OWASP Mobile M9 – Insecure Data Storage).
 *
 * - **EncryptedSharedPreferences**: llaves cifradas con AES-256-SIV y valores con AES-256-GCM.
 * - La llave maestra AES-256 vive en el **Android Keystore** (hardware cuando el teléfono lo tiene):
 *   no se puede extraer aunque alguien copie los archivos de la app.
 * - El archivo se crea con **MODE_PRIVATE**: solo esta app (su UID de Linux) puede leerlo.
 * - Nunca se escribe en la tarjeta SD / almacenamiento externo.
 *
 * Si existía la versión anterior sin cifrar, sus datos se migran una sola vez y el archivo viejo se borra.
 */
// Google marcó security-crypto como obsoleto (recomienda DataStore + Tink), pero la Actividad 3.2 pide
// EncryptedSharedPreferences explícitamente y sigue siendo seguro: se suprime solo ese aviso.
@Suppress("DEPRECATION")
internal object SecurePrefs {
    private const val ENCRYPTED_FILE = "prisma_secure_prefs"

    fun open(context: Context, legacyName: String? = null): SharedPreferences {
        val app = context.applicationContext
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encrypted = EncryptedSharedPreferences.create(
            app,
            ENCRYPTED_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        if (legacyName != null) migrate(app.getSharedPreferences(legacyName, Context.MODE_PRIVATE), encrypted, app, legacyName)
        return encrypted
    }

    private fun migrate(legacy: SharedPreferences, target: SharedPreferences, context: Context, legacyName: String) {
        if (legacy.all.isEmpty()) return
        target.edit().apply {
            legacy.all.forEach { (key, value) -> if (value is String) putString(key, value) }
        }.commit()
        legacy.edit().clear().commit()
        context.deleteSharedPreferences(legacyName)
    }
}
