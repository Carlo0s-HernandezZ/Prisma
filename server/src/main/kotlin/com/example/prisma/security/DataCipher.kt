package com.example.prisma.security

import com.example.prisma.config.AppConfig
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado de datos personales en reposo (PII) con **AES-256-GCM** (cifrado autenticado).
 *
 * - Llave de 256 bits en DATA_ENCRYPTION_KEY (Base64, en server/.env, nunca en el repositorio).
 * - IV aleatorio de 96 bits por cada valor: el mismo nombre cifrado dos veces produce textos distintos.
 * - La etiqueta GCM de 128 bits detecta cualquier modificación del valor guardado en la base de datos.
 *
 * Formato guardado: `enc:v1:` + Base64(IV ‖ texto cifrado ‖ etiqueta).
 * Valores antiguos sin el prefijo (guardados antes del cifrado) se leen tal cual y se cifran en la
 * siguiente actualización del perfil.
 */
object DataCipher {
    private val delegate: AesGcmCipher by lazy {
        val raw = AppConfig.get("DATA_ENCRYPTION_KEY")
            ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        require(raw != null && raw.size == 32) {
            "DATA_ENCRYPTION_KEY debe ser una llave AES-256 en Base64 (32 bytes). Genera una con: openssl rand -base64 32"
        }
        AesGcmCipher(raw)
    }

    /** Verifica al arrancar que la llave existe y es válida (falla rápido en vez de en la primera petición). */
    fun checkKey() {
        delegate
    }

    fun encrypt(plain: String): String = delegate.encrypt(plain)

    fun decrypt(stored: String): String = delegate.decrypt(stored)
}

/** Implementación de AES-256-GCM; [key] debe medir 32 bytes (256 bits). */
class AesGcmCipher(key: ByteArray) {
    init {
        require(key.size == 32) { "La llave AES-256 debe medir 32 bytes" }
    }

    private val secretKey = SecretKeySpec(key, "AES")
    private val random = SecureRandom()

    fun encrypt(plain: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv)) }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(iv + sealed)
    }

    fun decrypt(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored // dato heredado sin cifrar
        val bytes = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        }
        return cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).toString(Charsets.UTF_8)
    }

    companion object {
        const val PREFIX = "enc:v1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
