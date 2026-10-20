package com.example.prisma

import com.example.prisma.database.DatabaseFactory
import com.example.prisma.routes.authRoutes
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main(args: Array<String>) {
    io.ktor.server.engine.embeddedServer(
        factory = io.ktor.server.netty.Netty,
        port = 8080,
        host = "0.0.0.0", // Escuchar en todas las interfaces de red (Wi-Fi local)
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    // 1. Configurar la negociación de contenido para entender JSON
    install(ContentNegotiation) {
        json()
    }

    // 2. Iniciar la conexión a PostgreSQL
    DatabaseFactory.init()

    // 3. Registrar las rutas del servidor
    routing {
        get("/") {
            call.respondText(GreetingUtil().getGreeting())
        }

        // Endpoint para la Autenticación y 2FA (POST /api/v1/auth/login y /verify-2fa)
        authRoutes()
    }
}