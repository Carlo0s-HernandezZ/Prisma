package com.example.prisma.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object LecturasEstadoTable : Table("lecturas_automaticas_estado") {
    val id = uuid("id")

    val tarjetaId = uuid("tarjeta_id").references(
        TarjetasCreditoTable.id,
        onDelete = ReferenceOption.CASCADE,
    )

    val huella = varchar("huella", 64)
    val periodo = varchar("periodo", 7).nullable()
    val deteccion = text("deteccion")
    val version = integer("version")
    val versionFinanciera = integer("version_financiera").nullable()
    val fechaActualizacion = timestamp("fecha_actualizacion")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(tarjetaId, huella)
    }
}