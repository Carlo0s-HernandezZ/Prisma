package com.example.prisma

/** Puerto del servidor web Wasm que sirve la página legal. */
const val WEB_PORT = 3001

/** URL de la página pública de Términos; la base depende de la plataforma (ver [termsPageBaseUrl]). */
fun termsPageUrl(section: String? = null): String =
    termsPageBaseUrl() + (section?.let { "#$it" } ?: "")
