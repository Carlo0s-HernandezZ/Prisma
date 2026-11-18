package com.example.prisma

import com.example.prisma.auth.LoginRateLimiter
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory
import com.example.prisma.routes.authRoutes
import com.example.prisma.routes.biometricRoutes
import com.example.prisma.routes.cardRoutes
import com.example.prisma.routes.homeRoutes
import com.example.prisma.network.LocalNetworkAdvertiser
import com.example.prisma.routes.analysisRoutes
import com.example.prisma.routes.payrollAuditRoutes
import com.example.prisma.routes.incomeRoutes
import com.example.prisma.routes.profileRoutes
import com.example.prisma.routes.payrollRoutes
import com.example.prisma.api.ApiError
import com.example.prisma.config.AppConfig
import com.example.prisma.http.CSRF_HEADER
import com.example.prisma.security.DataCipher
import com.example.prisma.security.SecurityHeaders
import com.example.prisma.services.EmailService
import com.example.prisma.routes.incomePlanningRoutes
import com.example.prisma.routes.budgetRoutes
import com.example.prisma.routes.statementRoutes
import com.example.prisma.routes.statementConfirmationRoutes
import com.example.prisma.routes.statementPaymentsRoutes
import com.example.prisma.routes.automaticStatementRoutes
import com.example.prisma.routes.cardPlanRoutes
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

    // Se anuncia por mDNS para que los teléfonos lo encuentren en cualquier red, sin IP fija.
    LocalNetworkAdvertiser.start(HTTPS_PORT)

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
 * Orígenes web permitidos por CORS (las apps de Android/iOS no usan CORS). Por defecto:
 * http(s)://<host>:<puerto> con host = localhost, un nombre .local (Bonjour/mDNS) o una IP privada
 * (10.x, 172.16-31.x, 192.168.x), en los puertos de la app web (3000 JS, 3001 Wasm) y del servidor.
 * No depende de la red actual: la versión web funciona aunque cambie la IP, sin reiniciar.
 * Configurable con CORS_ALLOWED_HOSTS="host:puerto,otro:puerto" o "*" para permitir todos (solo desarrollo).
 */
internal fun isLocalWebOrigin(origin: String, ports: Set<Int>): Boolean {
    val uri = runCatching { java.net.URI(origin) }.getOrNull() ?: return false
    if (uri.scheme !in setOf("http", "https")) return false
    val host = uri.host?.lowercase() ?: return false
    if (uri.port !in ports) return false
    if (host == "localhost" || host == "127.0.0.1" || host.endsWith(".local")) return true
    val parts = host.split('.').mapNotNull(String::toIntOrNull)
    if (parts.size != 4 || host.count { it == '.' } != 3) return false
    return parts[0] == 10 ||
        (parts[0] == 172 && parts[1] in 16..31) ||
        (parts[0] == 192 && parts[1] == 168)
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
        } else if (configured != null) {
            configured.forEach { allowHost(it, schemes = listOf("http", "https")) }
        } else {
            // Funciona en cualquier red sin reiniciar: la app web de esta computadora o de la red local.
            val ports = setOf(3000, 3001, System.getenv("PORT")?.toIntOrNull() ?: 8080)
            allowOrigins { origin -> isLocalWebOrigin(origin, ports) }
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
        homeRoutes(sessions)
        incomeRoutes(sessions)
        incomePlanningRoutes(sessions)
        payrollRoutes(sessions)
        budgetRoutes(sessions)
        statementRoutes(sessions)
        statementConfirmationRoutes(sessions)
        statementPaymentsRoutes(sessions)
        automaticStatementRoutes(sessions)
        cardPlanRoutes(sessions)
        analysisRoutes(sessions)
        payrollAuditRoutes(sessions)
    }
}
