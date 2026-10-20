package com.example.prisma.routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import at.favre.lib.crypto.bcrypt.BCrypt

// Imports de Exposed para interactuar con la Base de Datos
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.select
import com.example.prisma.database.DatabaseFactory.dbQuery

// Imports de tus DTOs (Models)
import com.example.prisma.models.LoginRequest
import com.example.prisma.models.LoginResponse
import com.example.prisma.models.Verify2FaRequest
import com.example.prisma.models.Verify2FaResponse
import com.example.prisma.models.ErrorResponse
import com.example.prisma.models.RegisterRequest
import com.example.prisma.models.RegisterResponse
import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.qr.QrData
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider

import org.jetbrains.exposed.sql.insert

// 1. Mapeo exacto de la tabla "users" en tu base de datos PostgreSQL
object UsersTable : Table("users") {
    val id = uuid("id")
    val email = varchar("email", 255)
    val passwordHash = varchar("password_hash", 255)
    val secret2fa = varchar("secret_2fa", 100)
    val region = varchar("region", 50)

    override val primaryKey = PrimaryKey(id)
}

// 2. Modelo de Datos para representar al Usuario en memoria
data class User(
    val id: String,
    val email: String,
    val passwordHash: String,
    val secret2fa: String
)

// 3. Consultas Reales a PostgreSQL usando Exposed y Coroutines (dbQuery)
suspend fun findUserByEmail(emailParam: String): User? = dbQuery {
    UsersTable
        .select { UsersTable.email eq emailParam }
        .map { row ->
            User(
                id = row[UsersTable.id].toString(),
                email = row[UsersTable.email],
                passwordHash = row[UsersTable.passwordHash],
                secret2fa = row[UsersTable.secret2fa]
            )
        }
        .singleOrNull()
}

suspend fun findUserById(idParam: String): User? = dbQuery {
    val uuid = try { java.util.UUID.fromString(idParam) } catch (e: Exception) { return@dbQuery null }
    UsersTable
        .select { UsersTable.id eq uuid }
        .map { row ->
            User(
                id = row[UsersTable.id].toString(),
                email = row[UsersTable.email],
                passwordHash = row[UsersTable.passwordHash],
                secret2fa = row[UsersTable.secret2fa]
            )
        }
        .singleOrNull()
}
/**
fun verifyTotpCode(secret2fa: String, code: String): Boolean {
    // Para desarrollo rápido acepta "123456" o cualquier código de 6 dígitos válido.
    return code.length == 6
}**/

private val totpVerifier = DefaultCodeVerifier(
    DefaultCodeGenerator(),
    SystemTimeProvider()
).apply { setAllowedTimePeriodDiscrepancy(1) }

fun verifyTotpCode(secret2fa: String, code: String): Boolean =
    try {
        totpVerifier.isValidCode(secret2fa, code)
    } catch (e: Exception) {
        false
    }



// 4. Definición de las Rutas de Autenticación
fun Route.authRoutes() {
    route("/api/v1/auth") {

        // PETICIÓN 1: Login Primario (Password)
        post("/login") {
            val request = call.receive<LoginRequest>()
            System.err.println(">>> PETICIÓN RECIBIDA - Email: ${request.email}")

            // 1. Buscar en BD real
            val user = findUserByEmail(request.email)
            if (user == null) {
                System.err.println(">>> ERROR: Usuario no existe en PostgreSQL")
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("INVALID_CREDENTIALS", "Usuario o contraseña incorrectos", "2026-10-18T20:00:00Z")
                )
                return@post
            }

            System.err.println(">>> USUARIO ENCONTRADO: ${user.email} | Hash en BD: ${user.passwordHash}")

            // 2. Validar contraseña (BCrypt o texto plano temporal)
            val passwordMatch = if (user.passwordHash.startsWith("$2a$") || user.passwordHash.startsWith("$2b$")) {
                BCrypt.verifyer().verify(request.password.toCharArray(), user.passwordHash).verified
            } else {
                request.password == user.passwordHash
            }

            System.err.println(">>> VALIDEZ DE CONTRASEÑA: $passwordMatch")

            if (!passwordMatch) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("INVALID_CREDENTIALS", "Usuario o contraseña incorrectos", "2026-10-18T20:00:00Z")
                )
                return@post
            }

            // 3. Emitir temp_token corto (3 min)
            val tempToken = JWT.create()
                .withSubject(user.id)
                .withClaim("status", "PENDING_2FA")
                .withExpiresAt(Date(System.currentTimeMillis() + 3 * 60 * 1000))
                .sign(Algorithm.HMAC256("SECRET_KEY_TEMP"))

            System.err.println(">>> LOGIN EXITOSO - Emitiendo temp_token")

            call.respond(
                HttpStatusCode.OK,
                LoginResponse(
                    status = "PENDING_2FA",
                    temp_token = tempToken,
                    message = "Verificación de contraseña exitosa. Se requiere segundo factor."
                )
            )
        }

        // PETICIÓN 2: Verificación de Código 2FA
        post("/verify-2fa") {
            val request = call.receive<Verify2FaRequest>()

            // 1. Decodificar y validar temp_token
            val jwtVerifier = JWT.require(Algorithm.HMAC256("SECRET_KEY_TEMP")).build()
            val decodedJwt = try {
                jwtVerifier.verify(request.temp_token)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("EXPIRED_TEMP_TOKEN", "El token temporal ha expirado o es inválido", "2026-10-14T20:00:00Z")
                )
                return@post
            }

            val userId = decodedJwt.subject
            val user = findUserById(userId)
            if (user == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("USER_NOT_FOUND", "Usuario no encontrado", "2026-10-14T20:00:00Z")
                )
                return@post
            }

            // 2. Validar código TOTP con el secret_2fa de la BD
            val isTotpValid = verifyTotpCode(user.secret2fa, request.code)
            if (!isTotpValid) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("INVALID_2FA_CODE", "El código de autenticación provisto es incorrecto", "2026-10-14T20:00:00Z")
                )
                return@post
            }

            // 3. Emitir tokens definitivos de sesión
            val accessToken = JWT.create()
                .withSubject(user.id)
                .withExpiresAt(Date(System.currentTimeMillis() + 15 * 60 * 1000)) // 15 min
                .sign(Algorithm.HMAC256("SECRET_KEY_JWT"))

            val refreshToken = JWT.create()
                .withSubject(user.id)
                .withExpiresAt(Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000)) // 7 días
                .sign(Algorithm.HMAC256("SECRET_KEY_REFRESH"))

            call.respond(
                HttpStatusCode.OK,
                Verify2FaResponse(
                    status = "AUTHENTICATED",
                    access_token = accessToken,
                    refresh_token = refreshToken,
                    expires_in_seconds = 900
                )
            )


        }

        post("/register") {
            val request = call.receive<RegisterRequest>()

            if (findUserByEmail(request.email) != null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("EMAIL_EXISTS", "Ya existe un usuario con ese correo", java.time.Instant.now().toString())
                )
                return@post
            }

            val hash = BCrypt.withDefaults().hashToString(12, request.password.toCharArray())
            val secret = DefaultSecretGenerator().generate()

            dbQuery {
                UsersTable.insert {
                    it[id] = java.util.UUID.randomUUID()
                    it[email] = request.email
                    it[passwordHash] = hash
                    //it[secret2fa] = java.util.UUID.randomUUID().toString().replace("-", "").take(32)
                    it[secret2fa] = secret
                    it[region] = request.region
                }
            }

            val otpauthUri = QrData.Builder()
                .label(request.email)
                .secret(secret)
                .issuer("PRISMA")
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build()
                .uri

            call.respond(
                HttpStatusCode.Created,
                RegisterResponse("Usuario creado", secret, otpauthUri)
            )
        }


    }
}


