package com.example.prisma.routes

import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.api.ApiRules
import com.example.prisma.api.BiometricDeviceRequest
import com.example.prisma.api.BiometricEnrollRequest
import com.example.prisma.api.BiometricEnrollResponse
import com.example.prisma.api.BiometricLoginRequest
import com.example.prisma.api.Verify2faResponse
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory.dbQuery
import com.example.prisma.http.authenticatedSession
import com.example.prisma.http.respondNewSession
import com.example.prisma.http.receiveOrBadRequest
import com.example.prisma.http.respondError
import com.example.prisma.models.DispositivosBiometricosTable
import com.example.prisma.models.UsuariosTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

private val ALLOWED_PLATFORMS = setOf("android", "ios")
private val random = SecureRandom()

internal fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

private fun newDeviceToken(): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

/**
 * Acceso con huella (Android) o Face ID / Touch ID (iOS).
 *
 * La biometría se valida en el teléfono y desbloquea un token de dispositivo. El servidor
 * acepta ese token solo si el último código de Google Authenticator del dispositivo fue hace
 * menos de [ApiRules.TWO_FACTOR_WINDOW_HOURS] horas; después exige login completo con 2FA.
 */
fun Route.biometricRoutes(sessions: SessionStore) {
    route("/api/v1/auth/biometric") {

        /** Registra este dispositivo. Requiere sesión; la ventana de 24 h parte del 2FA de esa sesión. */
        post("/enroll") {
            val session = call.authenticatedSession(sessions) ?: return@post
            val req = call.receiveOrBadRequest<BiometricEnrollRequest>() ?: return@post
            val plataforma = req.plataforma.trim().lowercase()
            if (plataforma !in ALLOWED_PLATFORMS) {
                return@post call.respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, "Plataforma no soportada.")
            }

            val deviceId = UUID.randomUUID()
            val deviceToken = newDeviceToken()
            dbQuery {
                DispositivosBiometricosTable.insert {
                    it[id] = deviceId
                    it[usuarioId] = session.userId
                    it[tokenHash] = sha256Hex(deviceToken)
                    it[DispositivosBiometricosTable.plataforma] = plataforma
                    it[ultimo2fa] = Instant.ofEpochMilli(session.twoFactorAt)
                }
            }
            call.respond(HttpStatusCode.Created, BiometricEnrollResponse(deviceId.toString(), deviceToken))
        }

        /** Login con el token que el teléfono desbloqueó con biometría. */
        post("/login") {
            val req = call.receiveOrBadRequest<BiometricLoginRequest>() ?: return@post
            val device = dbQuery {
                (DispositivosBiometricosTable innerJoin UsuariosTable)
                    .selectAll()
                    .where { DispositivosBiometricosTable.tokenHash eq sha256Hex(req.deviceToken.trim()) }
                    .singleOrNull()
                    ?.let {
                        Triple(
                            it[DispositivosBiometricosTable.id],
                            it[DispositivosBiometricosTable.usuarioId] to it[UsuariosTable.correoElectronico],
                            it[DispositivosBiometricosTable.ultimo2fa].toEpochMilli(),
                        )
                    }
            } ?: return@post call.respondError(
                HttpStatusCode.Unauthorized, ApiErrorCodes.BIOMETRIC_REVOKED,
                "El acceso biométrico de este dispositivo ya no está activo. Inicia sesión con tu contraseña.",
            )

            val (deviceId, user, lastTwoFactor) = device

            dbQuery {
                DispositivosBiometricosTable.update({ DispositivosBiometricosTable.id eq deviceId }) {
                    it[ultimoUso] = Instant.ofEpochMilli(sessions.now())
                }
            }
            val accessToken = sessions.createSession(user.first, twoFactorAt = lastTwoFactor)
            call.respondNewSession(sessions, accessToken, user.second)
        }

        /** Tras un login con 2FA, reinicia la ventana de 24 h del dispositivo (sin pedir huella otra vez). */
        post("/renew") {
            val session = call.authenticatedSession(sessions) ?: return@post
            val req = call.receiveOrBadRequest<BiometricDeviceRequest>() ?: return@post
            val deviceId = req.deviceId.toUuidOrNull()
                ?: return@post call.respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, "Id inválido.")

            val current = dbQuery {
                DispositivosBiometricosTable.selectAll()
                    .where { (DispositivosBiometricosTable.id eq deviceId) and (DispositivosBiometricosTable.usuarioId eq session.userId) }
                    .singleOrNull()
                    ?.get(DispositivosBiometricosTable.ultimo2fa)
            } ?: return@post call.respondError(HttpStatusCode.NotFound, ApiErrorCodes.BIOMETRIC_REVOKED, "Dispositivo no registrado.")

            // Solo avanza: una sesión con un 2FA más viejo no puede acortar ni "reciclar" la ventana.
            val newest = maxOf(current.toEpochMilli(), session.twoFactorAt)
            dbQuery {
                DispositivosBiometricosTable.update({ DispositivosBiometricosTable.id eq deviceId }) {
                    it[ultimo2fa] = Instant.ofEpochMilli(newest)
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        /** Desactiva el acceso biométrico de un dispositivo propio. */
        post("/revoke") {
            val session = call.authenticatedSession(sessions) ?: return@post
            val req = call.receiveOrBadRequest<BiometricDeviceRequest>() ?: return@post
            val deviceId = req.deviceId.toUuidOrNull()
                ?: return@post call.respondError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, "Id inválido.")
            dbQuery {
                DispositivosBiometricosTable.deleteWhere {
                    (DispositivosBiometricosTable.id eq deviceId) and (DispositivosBiometricosTable.usuarioId eq session.userId)
                }
            }
            // Idempotente: si ya no existía, el resultado es el mismo (desactivado).
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
