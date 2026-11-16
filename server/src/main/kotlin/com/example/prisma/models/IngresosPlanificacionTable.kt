package com.example.prisma.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp

object IngresosPlanificacionTable : Table("ingresos_planificacion") {
    val ingresoId = uuid("ingreso_id").references(
        ref = IngresosTable.id,
        onDelete = ReferenceOption.CASCADE,
    )

    val fechaCobro = date("fecha_cobro")

    val tipoIngreso = varchar(
        name = "tipo_ingreso",
        length = 24,
    )

    val frecuencia = varchar(
        name = "frecuencia",
        length = 24,
    ).nullable()

    val fechaActualizacion = timestamp(
        name = "fecha_actualizacion",
    ).defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(ingresoId)
}