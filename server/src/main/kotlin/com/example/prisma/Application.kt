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
import com.example.prisma.routes.payrollRoutes
import com.example.prisma.api.ApiError
import com.example.prisma.config.AppConfig
import com.example.prisma.http.CSRF_HEADER
import com.example.prisma.security.DataCipher
import com.example.prisma.security.SecurityHeaders
import com.example.prisma.services.EmailService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.httpsredirect.HttpsRedirect
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.KeyStore

private val HTTP_PORT = AppConfig.int("HTTP_PORT", 8080)
private val HTTPS_PORT = AppConfig.int("HTTPS_PORT", 8443)

/** Solo TLS 1.3 y TLS 1.2. SSLv2, SSLv3, TLS 1.0 y TLS 1.1 quedan deshabilitados. */
private val TLS_PROTOCOLS = listOf("TLSv1.3", "TLSv1.2")

fun main() {
    val keystoreFile = File(AppConfig.get("KEYSTORE_PATH", "certs/prisma-keystore.p12"))
        .takeIf(File::isFile) ?: File("server", AppConfig.get("KEYSTORE_PATH", "certs/prisma-keystore.p12"))
    val keystorePassword = requireNotNull(AppConfig.get("KEYSTORE_PASSWORD")) { "Falta KEYSTORE_PASSWORD en server/.env" }
    require(keystoreFile.isFile) { "No existe ${keystoreFile.path}. Genera el certificado con server/certs/generate-cert.sh" }
    val keyStore = KeyStore.getInstance("PKCS12").apply {
        keystoreFile.inputStream().use { load(it, keystorePassword.toCharArray()) }
    }

    embeddedServer(
        Netty,
        configure = {
            // HTTP solo existe para redirigir a HTTPS (HttpsRedirect responde 301 a todo).
            connector {
                host = "0.0.0.0"
                port = HTTP_PORT
            }
            // 0.0.0.0: acepta conexiones de cualquier dispositivo de la red, no solo de esta computadora.
            sslConnector(
                keyStore = keyStore,
                keyAlias = "prisma",
                keyStorePassword = { keystorePassword.toCharArray() },
                privateKeyPassword = { keystorePassword.toCharArray() },
            ) {
                host = "0.0.0.0"
                port = HTTPS_PORT
                keyStorePath = keystoreFile
                enabledProtocols = TLS_PROTOCOLS
            }
        },
        module = { module() },
    ).start(wait = true)
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
    /** false solo en pruebas automáticas (el cliente de prueba no usa TLS). */
    enforceHttps: Boolean = true,
) {
    if (initDatabase) {
        DataCipher.checkKey()
        DatabaseFactory.init()
    }
    EmailService.logStartupStatus(this)
    log.info("Entorno: ${if (AppConfig.isDevelopment) "development" else "production"} · modo depuración de Ktor: $developmentMode")

    // ---------- HTTPS obligatorio ----------
    if (enforceHttps) {
        // Toda petición por HTTP recibe 301 → https://…:8443 con la misma ruta.
        install(HttpsRedirect) {
            sslPort = HTTPS_PORT
            permanentRedirect = true
        }
    }

    // ---------- Cabeceras de seguridad (HSTS, CSP, X-Frame-Options, nosniff…) ----------
    install(SecurityHeaders)

    // ---------- Errores sin trazas (debug = false) ----------
    // El cliente solo recibe un mensaje genérico; la traza completa queda únicamente en el log del servidor.
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Error no controlado en ${call.request.path()}: ${cause::class.simpleName}", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal_error", "Ocurrió un error interno. Inténtalo más tarde."))
        }
    }

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
        allowHeader(CSRF_HEADER)
        // Permite que el navegador envíe la cookie de sesión httpOnly en peticiones desde la app web.
        allowCredentials = true
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }

    routing {
        get("/") {
            call.respondText("Prisma API")
        }
        // Catálogo de endpoints (OpenAPI 3). La interfaz Swagger solo se publica en desarrollo
        // para no exponer la superficie de la API en producción.
        if (AppConfig.isDevelopment) {
            swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        }
        authRoutes(sessions, loginLimiter)
        biometricRoutes(sessions)
        cardRoutes(sessions)
        profileRoutes(sessions)
        dashboardRoutes(sessions)
        incomeRoutes(sessions)
        payrollRoutes(sessions)
    }
}
