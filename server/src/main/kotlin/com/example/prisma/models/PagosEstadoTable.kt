package com.example.prisma.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object PagosEstadoTable : Table("pagos_estado_cuenta") {
    val tarjetaId = uuid("tarjeta_id").references(
        TarjetasCreditoTable.id,
        onDelete = ReferenceOption.CASCADE,
    )

    val periodo = varchar("periodo", 7)
    val pagos = text("pagos")
    val version = integer("version")
    val versionEstado = integer("version_estado")
    val fechaActualizacion = timestamp("fecha_actualizacion")

    override val primaryKey = PrimaryKey(tarjetaId, periodo)
}