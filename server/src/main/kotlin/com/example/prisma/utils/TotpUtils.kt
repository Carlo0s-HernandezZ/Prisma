package com.example.prisma.utils

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer
import kotlin.experimental.and

object TotpUtils {
    fun verifyCode(secretBase32: String, code: String, timeWindow: Int = 1): Boolean {
        val cleanedSecret = secretBase32.replace(" ", "").uppercase()
        val secretBytes = base32Decode(cleanedSecret) ?: return false
        val currentTime = System.currentTimeMillis() / 1000L
        val timeStep = 30L

        for (i in -timeWindow..timeWindow) {
            val t = currentTime + (i * timeStep)
            val generatedCode = generateTotp(secretBytes, t)
            if (generatedCode == code) {
                return true
            }
        }
        return false
    }

    private fun generateTotp(secret: ByteArray, time: Long): String {
        val counter = time / 30L
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = (hash[hash.size - 1] and 0xf).toInt()
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)
        val otp = binary % 1000000
        return otp.toString().padStart(6, '0')
    }

    private fun base32Decode(s: String): ByteArray? {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bitsLeft = 0
        val output = mutableListOf<Byte>()
        for (char in s) {
            val valIndex = base32Chars.indexOf(char)
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
