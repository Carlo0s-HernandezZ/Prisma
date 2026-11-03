package com.example.prisma.utils

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TotpUtils {
    private const val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val TIME_STEP_SECONDS = 30L
    private val random = SecureRandom()

    /** Genera una clave secreta aleatoria de 160 bits (recomendado por RFC 4226), codificada en Base32 (32 caracteres). */
    fun generateSecret(): String {
        val bytes = ByteArray(20).also(random::nextBytes)
        return base32Encode(bytes)
    }

    fun verifyCode(secretBase32: String, code: String, timeWindow: Int = 1): Boolean {
        if (code.length != 6 || !code.all(Char::isDigit)) return false
        val secretBytes = base32Decode(secretBase32.replace(" ", "").uppercase()) ?: return false
        if (secretBytes.isEmpty()) return false
        val currentTime = System.currentTimeMillis() / 1000L

        var matched = false
        for (i in -timeWindow..timeWindow) {
            val generated = generateTotp(secretBytes, currentTime + i * TIME_STEP_SECONDS)
            // Comparación en tiempo constante para no filtrar información por tiempos de respuesta.
            if (MessageDigest.isEqual(generated.toByteArray(), code.toByteArray())) matched = true
        }
        return matched
    }

    internal fun generateTotp(secret: ByteArray, epochSeconds: Long, digits: Int = 6): String {
        val counter = epochSeconds / TIME_STEP_SECONDS
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash[hash.size - 1].toInt() and 0xf
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)
        var mod = 1
        repeat(digits) { mod *= 10 }
        return (binary % mod).toString().padStart(digits, '0')
    }

    internal fun base32Encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                sb.append(BASE32_CHARS[(buffer shr (bitsLeft - 5)) and 31])
                bitsLeft -= 5
            }
            buffer = buffer and ((1 shl bitsLeft) - 1)
        }
        if (bitsLeft > 0) sb.append(BASE32_CHARS[(buffer shl (5 - bitsLeft)) and 31])
        return sb.toString()
    }

    internal fun base32Decode(s: String): ByteArray? {
        var buffer = 0
        var bitsLeft = 0
        val output = mutableListOf<Byte>()
        for (char in s.trimEnd('=')) {
            val valIndex = BASE32_CHARS.indexOf(char)
            if (valIndex < 0) return null
            buffer = (buffer shl 5) or valIndex
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add((buffer shr bitsLeft).toByte())
                buffer = buffer and ((1 shl bitsLeft) - 1)
            }
        }
        return output.toByteArray()
    }
}
