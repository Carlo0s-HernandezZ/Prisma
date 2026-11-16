package com.example.prisma

/** Puerto del servidor web Wasm que sirve la página legal. */
const val WEB_PORT = 3001

fun termsPageBaseUrl(): String =
    "http://$SERVER_HOST:$WEB_PORT/terminos.html"

fun termsPageUrl(section: String? = null): String =
    termsPageBaseUrl() + (section?.let { "#$it" } ?: "")