package com.example.prisma.auth

import java.util.concurrent.ConcurrentHashMap

class PasswordResetStore(
    private val ttlMillis: Long = 15 * 60_000L, // 15 minutos
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class ResetRecord(val code: String, val expiresAt: Long)
    private val records = ConcurrentHashMap<String, ResetRecord>()

    fun createCode(email: String): String {
        val code = "%06d".format((0..999999).random())
        records[email.lowercase()] = ResetRecord(code, clock() + ttlMillis)
        return code
    }

    fun verifyAndConsume(email: String, code: String): Boolean {
        val normalized = email.lowercase()
        val record = records[normalized] ?: return false
        if (record.expiresAt <= clock() || record.code != code.trim()) {
            return false
        }
        records.remove(normalized)
        return true
    }
}
