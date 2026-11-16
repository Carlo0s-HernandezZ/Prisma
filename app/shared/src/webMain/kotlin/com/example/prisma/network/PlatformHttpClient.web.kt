package com.example.prisma.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * En el navegador, la validación TLS la hace el propio navegador: la API solo se consume por HTTPS
 * (ver BaseUrlProvider) y el servidor envía HSTS, así que el navegador nunca vuelve a usar HTTP.
 */
internal actual fun createPlatformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(config)
