package com.example.prisma

@OptIn(ExperimentalWasmJsInterop::class)
private fun pageHostname(): String = js("window.location.hostname")

// La página se abre como http://<ip-de-la-computadora>:3000; la API está en esa misma IP, solo por HTTPS.
actual fun defaultBaseUrl(): String = "https://${pageHostname().ifBlank { SERVER_HOST }}:$SERVER_PORT"
