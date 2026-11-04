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
 * Cada sesión recuerda cuándo se verificó el código 2FA ([SessionInfo.twoFactorAt]) y nunca
 * dura más de [twoFactorWindowMillis] desde ese momento, aunque provenga de un login biométrico.
 *
 * Los tokens son 256 bits aleatorios (no adivinables). Al reiniciar el servidor
 * todas las sesiones se pierden y los usuarios deben volver a iniciar sesión.
 */
class SessionStore(
    private val pendingTtlMillis: Long = 5 * 60_000L,
    private val sessionTtlMillis: Long = 12 * 60 * 60_000L,
    val twoFactorWindowMillis: Long = 24 * 60 * 60_000L,
    private val maxCodeAttempts: Int = 5,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Pending(val userId: UUID, val code: String, val expiresAt: Long) {
        var attempts = 0
    }

    private class Session(val userId: UUID, val expiresAt: Long, val twoFactorAt: Long)

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

    fun createPending(userId: UUID, email: String = "test@prisma.com", presetCode: String? = null, application: io.ktor.server.application.Application? = null): String {
        purgeExpired()
        val token = newToken()
        val code = presetCode ?: "%06d".format((0..999999).random())
        pending[token] = Pending(userId, code, clock() + pendingTtlMillis)

        if (presetCode == null) {
            com.example.prisma.services.EmailService.sendEmail(
                to = email,
                subject = "Código de Autenticación - Prisma",
                body = "Tu número de autenticación para ingresar a Prisma es: $code\n\nEste código expira en 5 minutos.",
                application = application,
            )
        }

        return token
    }

    /**
     * Registra un intento de código para el token temporal.
     * Devuelve el usuario si el código coincide y el token sigue vigente; si no, incrementa intentos y devuelve null.
     */
    fun attemptPending(token: String, code: String): UUID? {
        purgeExpired()
        var result: UUID? = null
        pending.computeIfPresent(token) { _, p ->
            if (p.expiresAt <= clock() || p.attempts >= maxCodeAttempts) {
                null
            } else {
                p.attempts++
                if (p.code == code.trim()) {
                    result = p.userId
                    null
                } else {
                    p
                }
            }
        }
        return result
    }

    fun completePending(token: String) {
        pending.remove(token)
    }

    /**
     * @param twoFactorAt momento (epoch ms) del último código 2FA válido. Para un login con 2FA es
     * "ahora"; para un login biométrico es el último 2FA registrado para ese dispositivo.
     */
    fun createSession(userId: UUID, twoFactorAt: Long = clock()): String {
        purgeExpired()
        val token = newToken()
        val expiresAt = minOf(clock() + sessionTtlMillis, twoFactorAt + twoFactorWindowMillis)
        sessions[token] = Session(userId, expiresAt, twoFactorAt)
        return token
    }

    fun sessionInfo(token: String): SessionInfo? {
        val s = sessions[token] ?: return null
        if (s.expiresAt <= clock()) {
            sessions.remove(token)
            return null
        }
        return SessionInfo(s.userId, s.twoFactorAt)
    }

    fun sessionUser(token: String): UUID? = sessionInfo(token)?.userId

    /** true si un 2FA hecho en [twoFactorAt] sigue dentro de la ventana permitida. */
    fun isTwoFactorFresh(twoFactorAt: Long): Boolean = clock() - twoFactorAt < twoFactorWindowMillis

    fun now(): Long = clock()

    fun revoke(token: String) {
        sessions.remove(token)
    }
}

class SessionInfo(val userId: UUID, val twoFactorAt: Long)

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
