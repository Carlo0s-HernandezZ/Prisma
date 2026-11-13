package com.example.prisma.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.example.prisma.config.AppConfig
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
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
 * El token de acceso es un **JWT firmado con HMAC-SHA256** (llave JWT_SECRET de server/.env) con
 * emisor, audiencia, sujeto (id del usuario), `jti` aleatorio de 256 bits y expiración. Además de
 * validar firma y expiración, el `jti` debe seguir registrado en el servidor: así el cierre de sesión
 * revoca el token aunque su firma siga siendo válida. Cada sesión tiene también un token anti-CSRF.
 *
 * El token temporal del 2FA sigue siendo un valor aleatorio de 256 bits (no adivinable).
 * Al reiniciar el servidor todas las sesiones se pierden y los usuarios deben volver a iniciar sesión.
 */
class SessionStore(
    private val pendingTtlMillis: Long = 5 * 60_000L,
    private val sessionTtlMillis: Long = 12 * 60 * 60_000L,
    val twoFactorWindowMillis: Long = 24 * 60 * 60_000L,
    private val maxCodeAttempts: Int = 5,
    private val clock: () -> Long = System::currentTimeMillis,
    jwtSecret: String? = AppConfig.get("JWT_SECRET"),
) {
    private val algorithm: Algorithm = Algorithm.HMAC256(
        jwtSecret?.takeIf { it.length >= 32 }
            ?: ByteArray(64).also(SecureRandom()::nextBytes).let { Base64.getEncoder().encodeToString(it) },
    )

    /** Reloj del verificador ligado a [clock] (las pruebas pueden simular el paso del tiempo). */
    private val verifier: JWTVerifier = (JWT.require(algorithm)
        .withIssuer(JWT_ISSUER)
        .withAudience(JWT_AUDIENCE) as JWTVerifier.BaseVerification)
        .build(object : Clock() {
            override fun instant(): Instant = Instant.ofEpochMilli(clock())
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId?): Clock = this
        })

    private class Pending(val userId: UUID, val code: String, val expiresAt: Long) {
        var attempts = 0
    }

    private class Session(val userId: UUID, val expiresAt: Long, val twoFactorAt: Long, val csrfToken: String)

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

    /**
     * Crea el reto 2FA tras validar usuario/contraseña. El código de 6 dígitos lo envía por correo
     * la ruta de login; aquí solo se genera y se guarda.
     */
    fun createChallenge(userId: UUID, presetCode: String? = null): PendingChallenge {
        purgeExpired()
        val token = newToken()
        val code = presetCode ?: "%06d".format(random.nextInt(1_000_000))
        pending[token] = Pending(userId, code, clock() + pendingTtlMillis)
        return PendingChallenge(token, code)
    }

    fun createPending(userId: UUID, presetCode: String? = null): String = createChallenge(userId, presetCode).token

    /**
     * Revisa el código del token temporal. Distingue "código incorrecto" (puede reintentar)
     * de "expirado o sin intentos" (debe iniciar sesión otra vez).
     */
    fun verifyPending(token: String, code: String): PendingResult {
        purgeExpired()
        var result: PendingResult = PendingResult.Expired
        pending.computeIfPresent(token) { _, p ->
            when {
                p.expiresAt <= clock() || p.attempts >= maxCodeAttempts -> null
                p.code == code.trim() -> {
                    result = PendingResult.Success(p.userId)
                    null // el código se consume
                }
                else -> {
                    p.attempts++
                    val remaining = maxCodeAttempts - p.attempts
                    result = if (remaining > 0) PendingResult.WrongCode(remaining) else PendingResult.Expired
                    if (remaining > 0) p else null
                }
            }
        }
        return result
    }

    /** Devuelve el usuario si el código es correcto; null si es incorrecto o expiró. */
    fun attemptPending(token: String, code: String): UUID? =
        (verifyPending(token, code) as? PendingResult.Success)?.userId

    fun completePending(token: String) {
        pending.remove(token)
    }

    /**
     * @param twoFactorAt momento (epoch ms) del último código 2FA válido. Para un login con 2FA es
     * "ahora"; para un login biométrico es el último 2FA registrado para ese dispositivo.
     */
    fun createSession(userId: UUID, twoFactorAt: Long = clock()): String {
        purgeExpired()
        val jti = newToken()
        val now = clock()
        val expiresAt = minOf(now + sessionTtlMillis, twoFactorAt + twoFactorWindowMillis)
        sessions[jti] = Session(userId, expiresAt, twoFactorAt, csrfToken = newToken())
        return JWT.create()
            .withIssuer(JWT_ISSUER)
            .withAudience(JWT_AUDIENCE)
            .withSubject(userId.toString())
            .withJWTId(jti)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(expiresAt))
            .sign(algorithm)
    }

    /** Valida firma, emisor, audiencia y expiración del JWT; devuelve su `jti` o null. */
    private fun verifiedJti(token: String): String? = try {
        verifier.verify(token).id
    } catch (_: JWTVerificationException) {
        null
    }

    fun sessionInfo(token: String): SessionInfo? {
        val jti = verifiedJti(token) ?: return null
        val s = sessions[jti] ?: return null // revocado (logout) o servidor reiniciado
        if (s.expiresAt <= clock()) {
            sessions.remove(jti)
            return null
        }
        return SessionInfo(s.userId, s.twoFactorAt, s.csrfToken)
    }

    fun sessionUser(token: String): UUID? = sessionInfo(token)?.userId

    /** true si un 2FA hecho en [twoFactorAt] sigue dentro de la ventana permitida. */
    fun isTwoFactorFresh(twoFactorAt: Long): Boolean = clock() - twoFactorAt < twoFactorWindowMillis

    fun now(): Long = clock()

    fun revoke(token: String) {
        verifiedJti(token)?.let(sessions::remove)
    }

    private companion object {
        const val JWT_ISSUER = "prisma-api"
        const val JWT_AUDIENCE = "prisma-clients"
    }
}

/** [csrfToken]: token anti-CSRF de la sesión; obligatorio en escrituras autenticadas por cookie. */
class SessionInfo(val userId: UUID, val twoFactorAt: Long, val csrfToken: String = "")

/** [token] viaja al cliente; [code] solo se envía al correo del usuario. */
class PendingChallenge(val token: String, val code: String)

sealed interface PendingResult {
    class Success(val userId: UUID) : PendingResult
    class WrongCode(val attemptsLeft: Int) : PendingResult
    data object Expired : PendingResult
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
