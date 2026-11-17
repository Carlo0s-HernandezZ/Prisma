package com.example.prisma

private fun pageHostname(): String = js("window.location.hostname")

// La página se abre como http://<ip-de-la-computadora>:3000; la API está en esa misma IP, solo por HTTPS.
actual fun defaultBaseUrl(): String = "https://${pageHostname().ifBlank { SERVER_HOST }}:$SERVER_PORT"

// Mismo servidor y puerto desde el que se abrió la app web.
internal actual fun termsPageBaseUrl(): String = "${pageOrigin()}/terminos.html"

private fun pageOrigin(): String = js("window.location.origin")
