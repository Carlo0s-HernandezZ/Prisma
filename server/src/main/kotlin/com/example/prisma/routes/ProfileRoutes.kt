package com.example.prisma.routes

import com.example.prisma.api.ApiErrorCodes
import com.example.prisma.api.ApiRules
import com.example.prisma.api.PersonalData
import com.example.prisma.api.PersonalDataResponse
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory.dbQuery
import com.example.prisma.http.authenticatedUser
import com.example.prisma.http.receiveOrBadRequest
import com.example.prisma.http.respondError
import com.example.prisma.models.DatosPersonalesTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** El usuario se obtiene del token de acceso, nunca de un parámetro: solo puedes ver o cambiar tu propio perfil. */
fun Route.profileRoutes(sessions: SessionStore) {
    route("/api/v1/user/profile") {

        get {
            val userId = call.authenticatedUser(sessions) ?: return@get
            val profile = dbQuery {
                DatosPersonalesTable.selectAll()
                    .where { DatosPersonalesTable.usuarioId eq userId }
                    .singleOrNull()
                    ?.let {
                        PersonalData(
                            nombre = it[DatosPersonalesTable.nombre],
                            apellido = it[DatosPersonalesTable.apellido],
                            fechaNacimiento = it[DatosPersonalesTable.fechaNacimiento]?.toString(),
                        )
                    }
            }
            if (profile == null) {
                call.respondError(HttpStatusCode.NotFound, ApiErrorCodes.NOT_FOUND, "Perfil no encontrado.")
            } else {
                call.respond(profile)
            }
        }

        post {
            val userId = call.authenticatedUser(sessions) ?: return@post
            val req = call.receiveOrBadRequest<PersonalData>() ?: return@post

            val nombre = req.nombre.trim()
            val apellido = req.apellido.trim()
            if (nombre.isEmpty() || apellido.isEmpty() ||
                nombre.length > ApiRules.MAX_NAME_LENGTH || apellido.length > ApiRules.MAX_NAME_LENGTH
            ) {
                return@post call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST,
                    "Nombre y apellido son obligatorios (máximo ${ApiRules.MAX_NAME_LENGTH} caracteres).",
                )
            }

            val fecha = req.fechaNacimiento?.takeIf { it.isNotBlank() }?.let {
                try {
                    LocalDate.parse(it)
                } catch (_: DateTimeParseException) {
                    return@post call.respondError(
                        HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, "Fecha de nacimiento inválida (usa AAAA-MM-DD).",
                    )
                }
            }
            if (fecha != null && fecha.isAfter(LocalDate.now())) {
                return@post call.respondError(
                    HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, "La fecha de nacimiento no puede ser futura.",
                )
            }

            val profileId = dbQuery {
                val existing = DatosPersonalesTable.selectAll()
                    .where { DatosPersonalesTable.usuarioId eq userId }
                    .singleOrNull()

                if (existing != null) {
                    DatosPersonalesTable.update({ DatosPersonalesTable.usuarioId eq userId }) {
                        it[DatosPersonalesTable.nombre] = nombre
                        it[DatosPersonalesTable.apellido] = apellido
                        it[DatosPersonalesTable.fechaNacimiento] = fecha
                        if (existing[DatosPersonalesTable.fechaCreacion] == null) {
                            it[DatosPersonalesTable.fechaCreacion] = CurrentTimestamp
                        }
                    }
                    existing[DatosPersonalesTable.id]
                } else {
                    DatosPersonalesTable.insert {
                        it[DatosPersonalesTable.usuarioId] = userId
                        it[DatosPersonalesTable.nombre] = nombre
                        it[DatosPersonalesTable.apellido] = apellido
                        it[DatosPersonalesTable.fechaNacimiento] = fecha
                    }[DatosPersonalesTable.id]
                }
            }
            call.respond(HttpStatusCode.OK, PersonalDataResponse("Datos personales guardados.", profileId))
        }

        delete {
            val userId = call.authenticatedUser(sessions) ?: return@delete
            val deleted = dbQuery { DatosPersonalesTable.deleteWhere { usuarioId eq userId } }
            if (deleted > 0) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respondError(HttpStatusCode.NotFound, ApiErrorCodes.NOT_FOUND, "Perfil no encontrado.")
            }
        }
    }
}
