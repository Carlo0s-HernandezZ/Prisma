package com.example.prisma.http

import com.example.prisma.api.ApiError
import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.auth.SessionInfo
import com.example.prisma.auth.SessionStore
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String, message: String) =
    respond(status, ApiError(code, message))

/** Lee el cuerpo JSON; si es inválido responde 400 y devuelve null (en vez de un 500). */
suspend inline fun <reified T : Any> ApplicationCall.receiveOrBadRequest(): T? =
    try {
        receive<T>()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, "Cuerpo de la solicitud inválido.")
        null
    }

fun ApplicationCall.bearerToken(): String? {
    val header = request.headers[HttpHeaders.Authorization] ?: return null
    if (!header.startsWith("Bearer ", ignoreCase = true)) return null
    return header.substring("Bearer ".length).trim().ifEmpty { null }
}

/** Como [authenticatedUser], pero incluye la hora del 2FA de la sesión. */
suspend fun ApplicationCall.authenticatedSession(sessions: SessionStore): SessionInfo? {
    val info = bearerToken()?.let(sessions::sessionInfo)
    if (info == null) {
        respondError(HttpStatusCode.Unauthorized, ApiErrorCodes.UNAUTHORIZED, "Sesión inválida o expirada.")
    }
    return info
}

/** Devuelve el usuario dueño del token de acceso; si no hay sesión válida responde 401 y devuelve null. */
suspend fun ApplicationCall.authenticatedUser(sessions: SessionStore): UUID? {
    val userId = bearerToken()?.let(sessions::sessionUser)
    if (userId == null) {
        respondError(HttpStatusCode.Unauthorized, ApiErrorCodes.UNAUTHORIZED, "Sesión inválida o expirada.")
    }
    return userId
}

private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

fun normalizeEmail(raw: String): String = raw.trim().lowercase()

fun isValidEmail(email: String): Boolean = email.length <= 255 && EMAIL_REGEX.matches(email)
