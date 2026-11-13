package com.example.prisma.security

import io.ktor.http.HttpHeaders
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.plugins.origin
import io.ktor.server.request.path

/**
 * Cabeceras HTTP de seguridad para todas las respuestas (equivalente a Helmet en Node.js).
 *
 * - Strict-Transport-Security (HSTS): solo en conexiones HTTPS, 1 año + subdominios.
 * - Content-Security-Policy: la API solo devuelve JSON, así que no permite cargar nada
 *   (`default-src 'none'`) ni ser enmarcada (`frame-ancestors 'none'`).
 * - X-Frame-Options: DENY (clickjacking) y X-Content-Type-Options: nosniff (MIME sniffing).
 * - Server: "Prisma" (no revela framework ni versión).
 */
val SecurityHeaders = createApplicationPlugin("SecurityHeaders") {
    // onCallRespond: se ejecuta justo antes de enviar CUALQUIER respuesta (rutas, errores, CORS,
    // redirecciones), incluso si otro plugin la generó antes de llegar a las rutas.
    onCallRespond { call, _ ->
        val headers = call.response.headers
        if (headers.contains("Content-Security-Policy")) return@onCallRespond
        val csp = if (call.request.path().startsWith("/swagger")) SWAGGER_CSP else API_CSP
        headers.append("Content-Security-Policy", csp)
        headers.append("X-Frame-Options", "DENY")
        headers.append("X-Content-Type-Options", "nosniff")
        headers.append("Referrer-Policy", "no-referrer")
        headers.append("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()")
        headers.append("Cross-Origin-Opener-Policy", "same-origin")
        headers.append("Cross-Origin-Resource-Policy", "same-site")
        headers.append(HttpHeaders.CacheControl, "no-store")
        headers.append(HttpHeaders.Server, "Prisma", safeOnly = false)
        if (call.request.origin.scheme == "https") {
            headers.append(HttpHeaders.StrictTransportSecurity, "max-age=31536000; includeSubDomains")
        }
    }
}

private const val API_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"

/** Swagger UI (solo en desarrollo) necesita cargar sus scripts y estilos desde unpkg. */
private const val SWAGGER_CSP =
    "default-src 'self'; script-src 'self' 'unsafe-inline' https://unpkg.com; " +
        "style-src 'self' 'unsafe-inline' https://unpkg.com; img-src 'self' data: https://unpkg.com; " +
        "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
