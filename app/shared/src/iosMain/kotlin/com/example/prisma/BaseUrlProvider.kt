package com.example.prisma

// SERVER_HOST se detecta al compilar (ver generateServerConfig en app/shared/build.gradle.kts).
// Solo HTTPS (TLS 1.2/1.3) con el certificado anclado de Prisma.
actual fun defaultBaseUrl(): String = "https://$SERVER_HOST:$SERVER_PORT"
