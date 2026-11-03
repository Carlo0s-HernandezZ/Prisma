package com.example.prisma.routes

import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.api.ApiRules
import com.example.prisma.api.LoginRequest
import com.example.prisma.api.LoginResponse
import com.example.prisma.api.RegisterRequest
import com.example.prisma.api.RegisterResponse
import com.example.prisma.api.Verify2faRequest
import com.example.prisma.api.Verify2faResponse
import com.example.prisma.auth.LoginRateLimiter
import com.example.prisma.auth.Passwords
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory.dbQuery
import com.example.prisma.http.bearerToken
import com.example.prisma.http.isValidEmail
import com.example.prisma.http.normalizeEmail
import com.example.prisma.http.receiveOrBadRequest
import com.example.prisma.http.respondError
import com.example.prisma.models.UsuariosTable
import com.example.prisma.utils.TotpUtils
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.net.URLEncoder
import java.util.UUID

private const val UNIQUE_VIOLATION = "23505"

fun Route.authRoutes(sessions: SessionStore, loginLimiter: LoginRateLimiter) {
    route("/api/v1/auth") {

        post("/register") {
            val req = call.receiveOrBadRequest<RegisterRequest>() ?: return@post
            val email = normalizeEmail(req.email)
            if (!isValidEmail(email)) {
                return@post call.respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, "Correo inválido.")
            }
            if (req.password.length < ApiRules.MIN_PASSWORD_LENGTH) {
                return@post call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.WEAK_PASSWORD,
                    "La contraseña debe tener al menos ${ApiRules.MIN_PASSWORD_LENGTH} caracteres.",
                )
            }
            val region = req.region.trim().uppercase().takeIf { it.length in 2..5 && it.all(Char::isLetter) } ?: "MX"

            // Cada usuario recibe su propia clave 2FA aleatoria.
            val secret = TotpUtils.generateSecret()
            val hashedPassword = withContext(Dispatchers.Default) { Passwords.hash(req.password) }

            val created = try {
                dbQuery {
                    val exists = UsuariosTable.selectAll()
                        .where { UsuariosTable.correoElectronico.lowerCase() eq email }
                        .any()
                    if (exists) {
                        false
                    } else {
                        UsuariosTable.insert {
                            it[id] = UUID.randomUUID()
                            it[correoElectronico] = email
                            it[hashContrasena] = hashedPassword
                            it[secreta2fa] = secret
                            it[UsuariosTable.region] = region
                        }
                        true
                    }
                }
            } catch (e: ExposedSQLException) {
                // Dos registros simultáneos con el mismo correo: el índice único rechaza el segundo.
                if (e.sqlState == UNIQUE_VIOLATION) false else throw e
            }

            if (!created) {
                return@post call.respondError(HttpStatusCode.Conflict, ApiErrorCodes.EMAIL_TAKEN, "El correo ya está registrado.")
            }

            val label = URLEncoder.encode("Prisma:$email", "UTF-8").replace("+", "%20")
            val otpauthUrl = "otpauth://totp/$label?secret=$secret&issuer=Prisma"
            call.respond(HttpStatusCode.Created, RegisterResponse("Usuario registrado.", secret, otpauthUrl))
        }

        post("/login") {
            val req = call.receiveOrBadRequest<LoginRequest>() ?: return@post
            val email = normalizeEmail(req.email)

            if (loginLimiter.isBlocked(email)) {
                return@post call.respondError(
                    HttpStatusCode.TooManyRequests, ApiErrorCodes.TOO_MANY_ATTEMPTS,
                    "Demasiados intentos fallidos. Espera unos minutos.",
                )
            }

            val user = dbQuery {
                UsuariosTable.selectAll()
                    .where { UsuariosTable.correoElectronico.lowerCase() eq email }
                    .singleOrNull()
                    ?.let { it[UsuariosTable.id] to it[UsuariosTable.hashContrasena] }
            }
            val storedHash = user?.second
            val valid = withContext(Dispatchers.Default) { Passwords.verify(req.password, storedHash) }

            if (user == null || !valid) {
                loginLimiter.recordFailure(email)
                return@post call.respondError(
                    HttpStatusCode.Unauthorized, ApiErrorCodes.INVALID_CREDENTIALS, "Correo o contraseña incorrectos.",
                )
            }
            loginLimiter.reset(email)

            // Migra contraseñas heredadas en texto plano a BCrypt la primera vez que el usuario entra.
            if (storedHash != null && !Passwords.isBcrypt(storedHash)) {
                val newHash = withContext(Dispatchers.Default) { Passwords.hash(req.password) }
                dbQuery {
                    UsuariosTable.update({ UsuariosTable.id eq user.first }) {
                        it[hashContrasena] = newHash
                        it[fechaActualizacion] = CurrentTimestamp
                    }
                }
                call.application.log.info("Contraseña heredada migrada a BCrypt para el usuario ${user.first}")
            }

            call.respond(LoginResponse(sessions.createPending(user.first)))
        }

        post("/verify-2fa") {
            val req = call.receiveOrBadRequest<Verify2faRequest>() ?: return@post

            val userId = sessions.attemptPending(req.tempToken)
                ?: return@post call.respondError(
                    HttpStatusCode.Gone, ApiErrorCodes.SESSION_EXPIRED, "La verificación expiró. Inicia sesión de nuevo.",
                )

            val user = dbQuery {
                UsuariosTable.selectAll()
                    .where { UsuariosTable.id eq userId }
                    .singleOrNull()
                    ?.let { it[UsuariosTable.correoElectronico] to it[UsuariosTable.secreta2fa] }
            }
            if (user == null) {
                sessions.completePending(req.tempToken)
                return@post call.respondError(
                    HttpStatusCode.Gone, ApiErrorCodes.SESSION_EXPIRED, "La verificación expiró. Inicia sesión de nuevo.",
                )
            }

            if (!TotpUtils.verifyCode(user.second, req.code.trim())) {
                return@post call.respondError(HttpStatusCode.Unauthorized, ApiErrorCodes.INVALID_CODE, "Código incorrecto.")
            }

            sessions.completePending(req.tempToken)
            call.respond(Verify2faResponse(accessToken = sessions.createSession(userId), email = user.first))
        }

        post("/logout") {
            call.bearerToken()?.let(sessions::revoke)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
