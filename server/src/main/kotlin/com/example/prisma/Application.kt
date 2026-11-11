package com.example.prisma

import com.example.prisma.auth.LoginRateLimiter
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory
import com.example.prisma.routes.authRoutes
import com.example.prisma.routes.biometricRoutes
import com.example.prisma.routes.cardRoutes
import com.example.prisma.routes.dashboardRoutes
import com.example.prisma.routes.incomeRoutes
import com.example.prisma.routes.profileRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import com.example.prisma.services.EmailService
import java.net.Inet4Address
import java.net.NetworkInterface

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    // 0.0.0.0: acepta conexiones de cualquier dispositivo de la red, no solo de esta computadora.
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module() }
        .start(wait = true)
}

/**
 * Orígenes web permitidos por CORS (las apps de Android/iOS no usan CORS).
 * Por defecto: localhost y todas las IPs de red local de esta computadora, en los puertos del
 * servidor de desarrollo web (3000 JS, 3001 Wasm) y del propio servidor. Así la versión web
 * funciona desde cualquier dispositivo de la misma red aunque cambie la IP.
 * Configurable con CORS_ALLOWED_HOSTS="host:puerto,otro:puerto" o "*" para permitir todos (solo desarrollo).
 */
private fun defaultCorsHosts(serverPort: Int): List<String> {
    val lanIps = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress }
    }.getOrDefault(emptyList())
    val hosts = listOf("localhost", "127.0.0.1") + lanIps
    val ports = listOf(3000, 3001, serverPort).distinct()
    return hosts.flatMap { host -> ports.map { "$host:$it" } }
}

fun Application.module(
    initDatabase: Boolean = true,
    sessions: SessionStore = SessionStore(),
    loginLimiter: LoginRateLimiter = LoginRateLimiter(),
) {
    if (initDatabase) DatabaseFactory.init()
    EmailService.logStartupStatus(this)

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(CORS) {
        val configured = System.getenv("CORS_ALLOWED_HOSTS")
            ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
        if (configured == listOf("*")) {
            anyHost()
        } else {
            (configured ?: defaultCorsHosts(System.getenv("PORT")?.toIntOrNull() ?: 8080)).forEach { allowHost(it, schemes = listOf("http", "https")) }
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }

    routing {
        get("/") {
            call.respondText("Prisma API")
        }
        authRoutes(sessions, loginLimiter)
        biometricRoutes(sessions)
        cardRoutes(sessions)
        profileRoutes(sessions)
        dashboardRoutes(sessions)
        incomeRoutes(sessions)
    }
}
