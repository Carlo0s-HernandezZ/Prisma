package com.example.prisma

/**
 * Página pública (sin iniciar sesión) con los Términos y Condiciones, el Aviso de Privacidad y la
 * Seguridad de PRISMA: `app/webApp/src/webMain/resources/terminos.html`, servida por la versión web.
 * [section]: ancla opcional, p. ej. "parte-a" (Términos) o "parte-b" (Aviso de Privacidad).
 */
fun termsPageUrl(section: String? = null): String =
    termsPageBaseUrl() + (section?.let { "#$it" } ?: "")

/** Puerto del servidor web de PRISMA (versión Wasm). */
const val WEB_PORT = 3001
