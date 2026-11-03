package com.example.prisma

import com.example.prisma.auth.LoginRateLimiter
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory
import com.example.prisma.routes.authRoutes
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

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module() }
        .start(wait = true)
}

/**
 * Orígenes web permitidos por CORS (las apps de Android/iOS no usan CORS).
 * Configurable con CORS_ALLOWED_HOSTS="host:puerto,otro:puerto" o "*" para permitir todos (solo desarrollo).
 */
private val DEFAULT_CORS_HOSTS = listOf("localhost:3000", "127.0.0.1:3000", "localhost:3001", "127.0.0.1:3001")

fun Application.module(
    initDatabase: Boolean = true,
    sessions: SessionStore = SessionStore(),
    loginLimiter: LoginRateLimiter = LoginRateLimiter(),
) {
    if (initDatabase) DatabaseFactory.init()

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(CORS) {
        val configured = System.getenv("CORS_ALLOWED_HOSTS")
            ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
        if (configured == listOf("*")) {
            anyHost()
        } else {
            (configured ?: DEFAULT_CORS_HOSTS).forEach { allowHost(it, schemes = listOf("http", "https")) }
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
        profileRoutes(sessions)
        dashboardRoutes(sessions)
        incomeRoutes(sessions)
    }
}
