package com.example.prisma.auth

import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Sesiones en memoria, seguras para uso concurrente.
 *
 * - Token temporal (después de usuario/contraseña): caduca en [pendingTtlMillis] y
 *   admite como máximo [maxCodeAttempts] intentos de código 2FA.
 * - Token de acceso (después del 2FA): caduca en [sessionTtlMillis].
 *
 * Los tokens son 256 bits aleatorios (no adivinables). Al reiniciar el servidor
 * todas las sesiones se pierden y los usuarios deben volver a iniciar sesión.
 */
class SessionStore(
    private val pendingTtlMillis: Long = 5 * 60_000L,
    private val sessionTtlMillis: Long = 12 * 60 * 60_000L,
    private val maxCodeAttempts: Int = 5,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Pending(val userId: UUID, val expiresAt: Long) {
        var attempts = 0
    }

    private class Session(val userId: UUID, val expiresAt: Long)

    private val pending = ConcurrentHashMap<String, Pending>()
    private val sessions = ConcurrentHashMap<String, Session>()
    private val random = SecureRandom()

    private fun newToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun purgeExpired() {
        val now = clock()
        pending.values.removeIf { it.expiresAt <= now }
        sessions.values.removeIf { it.expiresAt <= now }
    }

    fun createPending(userId: UUID): String {
        purgeExpired()
        val token = newToken()
        pending[token] = Pending(userId, clock() + pendingTtlMillis)
        return token
    }

    /**
     * Registra un intento de código para el token temporal.
     * Devuelve el usuario si el token sigue vigente y le quedan intentos; si no, lo invalida y devuelve null.
     */
    fun attemptPending(token: String): UUID? {
        var result: UUID? = null
        pending.computeIfPresent(token) { _, p ->
            if (p.expiresAt <= clock() || p.attempts >= maxCodeAttempts) {
                null
            } else {
                p.attempts++
                result = p.userId
                p
            }
        }
        return result
    }

    fun completePending(token: String) {
        pending.remove(token)
    }

    fun createSession(userId: UUID): String {
        purgeExpired()
        val token = newToken()
        sessions[token] = Session(userId, clock() + sessionTtlMillis)
        return token
    }

    fun sessionUser(token: String): UUID? {
        val s = sessions[token] ?: return null
        if (s.expiresAt <= clock()) {
            sessions.remove(token)
            return null
        }
        return s.userId
    }

    fun revoke(token: String) {
        sessions.remove(token)
    }
}

/**
 * Limita intentos fallidos de login por correo para frenar ataques de fuerza bruta.
 */
class LoginRateLimiter(
    private val maxFailures: Int = 5,
    private val windowMillis: Long = 15 * 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Window(val startedAt: Long, val failures: Int)

    private val failures = ConcurrentHashMap<String, Window>()

    fun isBlocked(key: String): Boolean {
        val w = failures[key] ?: return false
        if (clock() - w.startedAt > windowMillis) {
            failures.remove(key)
            return false
        }
        return w.failures >= maxFailures
    }

    fun recordFailure(key: String) {
        val now = clock()
        failures.compute(key) { _, w ->
            if (w == null || now - w.startedAt > windowMillis) Window(now, 1) else Window(w.startedAt, w.failures + 1)
        }
    }

    fun reset(key: String) {
        failures.remove(key)
    }
}
