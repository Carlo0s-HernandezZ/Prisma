package com.example.prisma

import com.example.prisma.db.DatabaseFactory
import com.example.prisma.models.*
import com.example.prisma.utils.TotpUtils
import io.ktor.http.*
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.net.URLEncoder
import java.util.UUID

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(val tempToken: String)

@Serializable
data class Verify2faRequest(val tempToken: String, val code: String)

@Serializable
data class Verify2faResponse(val accessToken: String)

@Serializable
data class RegisterRequest(val email: String, val password: String, val region: String = "MX")

@Serializable
data class RegisterResponse(
    val message: String,
    val secret2fa: String,
    val qrCodeUrl: String,
    val otpauthUrl: String
)

@Serializable
data class PaymentAlertDto(val bankName: String, val lastFour: String, val daysLeft: Int)

@Serializable
data class DashboardResponse(
    val userName: String,
    val status: String,
    val radarAxes: List<String>,
    val alerts: List<PaymentAlertDto>,
    val totalToPayMxn: Double
)

private val activeTempTokens = mutableMapOf<String, String>()

fun Application.module() {
    DatabaseFactory.init()

    install(ContentNegotiation) {
        json()
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }

    routing {
        get("/") {
            call.respondText("Prisma API running with QR Code & TOTP 2FA")
        }

        post("/api/v1/auth/register") {
            val req = call.receive<RegisterRequest>()
            try {
                val defaultSecret = "JBSWY3DPEHPK3PXP"
                val otpauthUrl = "otpauth://totp/Prisma:${req.email}?secret=$defaultSecret&issuer=Prisma"
                val qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=${URLEncoder.encode(otpauthUrl, "UTF-8")}"

                val newId = DatabaseFactory.dbQuery {
                    val existing = UsuariosTable.selectAll()
                        .where { UsuariosTable.correoElectronico eq req.email }
                        .singleOrNull()
                    if (existing != null) {
                        null
                    } else {
                        UsuariosTable.insert {
                            it[id] = UUID.randomUUID()
                            it[correoElectronico] = req.email
                            it[hashContrasena] = req.password
                            it[secreta2fa] = defaultSecret
                            it[region] = req.region
                        }[UsuariosTable.id]
                    }
                }

                if (newId != null) {
                    call.respond(HttpStatusCode.Created, RegisterResponse("User registered successfully", defaultSecret, qrCodeUrl, otpauthUrl))
                } else {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Email already registered"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Registration failed")))
            }
        }

        post("/api/v1/auth/login") {
            val req = call.receive<LoginRequest>()
            val userFound = DatabaseFactory.dbQuery {
                UsuariosTable.selectAll()
                    .where { UsuariosTable.correoElectronico eq req.email }
                    .singleOrNull()
            }

            if (userFound != null || (req.email.contains("@") && req.password.isNotBlank())) {
                val tempToken = "prisma_temp_token_${System.currentTimeMillis()}"
                activeTempTokens[tempToken] = req.email
                call.respond(LoginResponse(tempToken))
            } else {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
            }
        }

        post("/api/v1/auth/verify-2fa") {
            val req = call.receive<Verify2faRequest>()
            val email = activeTempTokens[req.tempToken]
            
            val user = email?.let { em ->
                DatabaseFactory.dbQuery {
                    UsuariosTable.selectAll()
                        .where { UsuariosTable.correoElectronico eq em }
                        .singleOrNull()
                }
            }

            val secret2fa = user?.get(UsuariosTable.secreta2fa) ?: "JBSWY3DPEHPK3PXP"
            val isValid = TotpUtils.verifyCode(secret2fa, req.code)

            if (isValid) {
                activeTempTokens.remove(req.tempToken)
                call.respond(Verify2faResponse("prisma_access_token_${System.currentTimeMillis()}"))
            } else {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid 2FA code"))
            }
        }

        get("/api/v1/reports/latest") {
            val alerts = listOf(
                PaymentAlertDto("Citi", "1635", 5),
                PaymentAlertDto("Nu", "3489", 3),
                PaymentAlertDto("MPago", "7630", 9)
            )
            call.respond(
                DashboardResponse(
                    userName = "Casandra",
                    status = "Critical",
                    radarAxes = listOf("Capacidad de Pago", "Alertas MSI", "Nivel de Ahorro", "Control de Gastos", "Uso de Crédito"),
                    alerts = alerts,
                    totalToPayMxn = 15000.0
                )
            )
        }
    }
}
