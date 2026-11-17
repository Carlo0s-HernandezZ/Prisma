package com.example.prisma

// SERVER_HOST se detecta al compilar (ver generateServerConfig en app/shared/build.gradle.kts).
// Solo HTTPS (TLS 1.2/1.3) con el certificado anclado de Prisma.
actual fun defaultBaseUrl(): String = "https://$SERVER_HOST:$SERVER_PORT"

// Se abre en el navegador del teléfono: la página es pública y la sirve la versión web de PRISMA.
// ?origen=ios: la página muestra «Volver a Prisma» que reabre esta app (prisma://abrir).
internal actual fun termsPageBaseUrl(): String = "http://$SERVER_HOST:$WEB_PORT/terminos.html?origen=ios"
