package com.example.prisma.auth

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Códigos de recuperación de contraseña (6 dígitos, enviados por correo).
 *
 * - Generados con [SecureRandom] (no predecibles).
 * - Caducan en [ttlMillis] y admiten como máximo [maxAttempts] intentos: con 6 dígitos hay un millón
 *   de combinaciones, así que sin límite se podrían probar todas por fuerza bruta en 15 minutos.
 * - Pedir un código nuevo invalida el anterior; un código correcto se consume (un solo uso).
 * - Máximo [maxRequests] solicitudes por correo cada [ttlMillis]: si no, pedir códigos sin parar
 *   reiniciaría los intentos y la fuerza bruta volvería a ser posible.
 */
class PasswordResetStore(
    private val ttlMillis: Long = 15 * 60_000L,
    private val maxAttempts: Int = 5,
    private val maxRequests: Int = 3,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class ResetRecord(val code: String, val expiresAt: Long) {
        var attempts = 0
    }

    private class RequestWindow(val startedAt: Long, val count: Int)

    private val records = ConcurrentHashMap<String, ResetRecord>()
    private val requests = ConcurrentHashMap<String, RequestWindow>()
    private val random = SecureRandom()

    /** Registra una solicitud de código; false si el correo ya pidió [maxRequests] en la ventana actual. */
    fun allowRequest(email: String): Boolean {
        val now = clock()
        val window = requests.compute(email.lowercase()) { _, w ->
            if (w == null || now - w.startedAt > ttlMillis) RequestWindow(now, 1) else RequestWindow(w.startedAt, w.count + 1)
        }!!
        return window.count <= maxRequests
    }

    fun createCode(email: String): String {
        val code = "%06d".format(random.nextInt(1_000_000))
        records[email.lowercase()] = ResetRecord(code, clock() + ttlMillis)
        return code
    }

    fun verifyAndConsume(email: String, code: String): ResetResult {
        val key = email.lowercase()
        var result: ResetResult = ResetResult.Expired
        records.computeIfPresent(key) { _, r ->
            when {
                r.expiresAt <= clock() || r.attempts >= maxAttempts -> null
                r.code == code.trim() -> {
                    result = ResetResult.Success
                    null // un solo uso
                }
                else -> {
                    r.attempts++
                    val left = maxAttempts - r.attempts
                    result = if (left > 0) ResetResult.WrongCode(left) else ResetResult.Expired
                    if (left > 0) r else null
                }
            }
        }
        return result
    }
}

sealed interface ResetResult {
    data object Success : ResetResult
    class WrongCode(val attemptsLeft: Int) : ResetResult
    /** No hay código, caducó o se agotaron los intentos: hay que pedir uno nuevo. */
    data object Expired : ResetResult
}
