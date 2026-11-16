package com.example.prisma.http

import com.example.prisma.api.ApiError
import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.auth.SessionInfo
import com.example.prisma.auth.SessionStore
import com.example.prisma.api.Verify2faResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import java.security.MessageDigest
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

// ---------------------------------------------------------------------------------------------
// Sesión por cookie (navegador) + protección CSRF
// ---------------------------------------------------------------------------------------------
//
// Las apps móviles envían el JWT en la cabecera Authorization: Bearer (no es vulnerable a CSRF
// porque el navegador nunca la agrega solo). Para el navegador, el servidor también entrega el JWT
// en una cookie httpOnly + Secure + SameSite=Strict con prefijo __Host- (solo HTTPS, sin Domain,
// Path=/). Toda escritura (POST/PUT/PATCH/DELETE) autenticada con esa cookie exige además la
// cabecera X-CSRF-Token con el token anti-CSRF único e impredecible de la sesión.

const val SESSION_COOKIE = "__Host-prisma_session"
const val CSRF_COOKIE = "__Host-prisma_csrf"
const val CSRF_HEADER = "X-CSRF-Token"

private val WRITE_METHODS = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete)

/** Duración de las cookies; la sesión real la controla el servidor (puede expirar antes). */
private const val COOKIE_MAX_AGE_SECONDS = 12 * 60 * 60

/** JWT de la petición y si llegó por cookie (true) o por cabecera Authorization (false). */
private fun ApplicationCall.accessToken(): Pair<String, Boolean>? =
    bearerToken()?.let { it to false }
        ?: request.cookies[SESSION_COOKIE]?.takeIf(String::isNotBlank)?.let { it to true }

/** Comparación en tiempo constante (evita ataques de temporización sobre el token CSRF). */
private fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

/** Como [authenticatedUser], pero incluye la hora del 2FA de la sesión. */
suspend fun ApplicationCall.authenticatedSession(sessions: SessionStore): SessionInfo? {
    val (token, fromCookie) = accessToken() ?: run {
        respondError(HttpStatusCode.Unauthorized, ApiErrorCodes.UNAUTHORIZED, "Sesión inválida o expirada.")
        return null
    }
    val info = sessions.sessionInfo(token)
    if (info == null) {
        respondError(HttpStatusCode.Unauthorized, ApiErrorCodes.UNAUTHORIZED, "Sesión inválida o expirada.")
        return null
    }
    if (fromCookie && request.httpMethod in WRITE_METHODS) {
        val sent = request.headers[CSRF_HEADER].orEmpty()
        if (sent.isEmpty() || !constantTimeEquals(sent, info.csrfToken)) {
            respondError(HttpStatusCode.Forbidden, ApiErrorCodes.CSRF_INVALID, "Token anti-CSRF ausente o inválido.")
            return null
        }
    }
    return info
}

/** Devuelve el usuario dueño del token de acceso; si no hay sesión válida responde 401 y devuelve null. */
suspend fun ApplicationCall.authenticatedUser(sessions: SessionStore): UUID? =
    authenticatedSession(sessions)?.userId

/**
 * Escribe la cabecera Set-Cookie explícitamente para controlar cada atributo:
 * `Secure` (solo HTTPS), `HttpOnly` (inaccesible para JavaScript → mitiga robo por XSS),
 * `SameSite=Strict` (no viaja en peticiones iniciadas por otros sitios) y `Path=/`.
 */
private fun ApplicationCall.setCookie(name: String, value: String, httpOnly: Boolean, maxAge: Int) {
    val attributes = buildList {
        add("$name=$value")
        add("Max-Age=$maxAge")
        add("Path=/")
        add("Secure")
        if (httpOnly) add("HttpOnly")
        add("SameSite=Strict")
    }
    response.headers.append(HttpHeaders.SetCookie, attributes.joinToString("; "))
}

/**
 * Responde al inicio de sesión: JWT en el cuerpo (apps móviles) y en la cookie httpOnly/Secure
 * (navegador), más el token anti-CSRF (cookie legible por JS + campo del cuerpo).
 */
suspend fun ApplicationCall.respondNewSession(sessions: SessionStore, accessToken: String, email: String) {
    val csrf = sessions.sessionInfo(accessToken)?.csrfToken.orEmpty()
    setCookie(SESSION_COOKIE, accessToken, httpOnly = true, maxAge = COOKIE_MAX_AGE_SECONDS)
    setCookie(CSRF_COOKIE, csrf, httpOnly = false, maxAge = COOKIE_MAX_AGE_SECONDS)
    respond(Verify2faResponse(accessToken = accessToken, email = email, csrfToken = csrf))
}

/** Cierra la sesión: revoca el JWT (cabecera o cookie) y borra las cookies del navegador. */
fun ApplicationCall.endSession(sessions: SessionStore) {
    bearerToken()?.let(sessions::revoke)
    request.cookies[SESSION_COOKIE]?.let(sessions::revoke)
    setCookie(SESSION_COOKIE, "", httpOnly = true, maxAge = 0)
    setCookie(CSRF_COOKIE, "", httpOnly = false, maxAge = 0)
}

private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

fun normalizeEmail(raw: String): String = raw.trim().lowercase()

fun isValidEmail(email: String): Boolean = email.length <= 255 && EMAIL_REGEX.matches(email)
