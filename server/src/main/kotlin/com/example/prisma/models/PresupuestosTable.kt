package com.example.prisma.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp

object PresupuestosTable : Table("presupuestos_usuario") {
    val usuarioId = uuid("usuario_id").references(
        UsuariosTable.id,
        onDelete = ReferenceOption.CASCADE,
    )

    val periodo = varchar("periodo", 7)
    val fechaBase = date("fecha_base")
    val saldoDisponible = decimal("saldo_disponible", 12, 2)
    val reserva = decimal("reserva", 12, 2)
    val movimientos = text("movimientos")
    val version = integer("version").default(1)

    val fechaActualizacion = timestamp("fecha_actualizacion")
        .defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(usuarioId, periodo)
}