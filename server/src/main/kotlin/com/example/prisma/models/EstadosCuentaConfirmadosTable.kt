package com.example.prisma.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object EstadosCuentaConfirmadosTable : Table("estados_cuenta_confirmados") {
    val tarjetaId = uuid("tarjeta_id").references(
        TarjetasCreditoTable.id,
        onDelete = ReferenceOption.CASCADE,
    )

    val periodo = varchar("periodo", 7)
    val campos = text("campos")
    val version = integer("version")
    val fechaConfirmacion = timestamp("fecha_confirmacion")

    override val primaryKey = PrimaryKey(tarjetaId, periodo)
}