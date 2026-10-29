package com.example.prisma.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import java.math.BigDecimal

object UsuariosTable : Table("usuarios") {
    val id = uuid("id")
    val correoElectronico = varchar("correo_electronico", 255).uniqueIndex()
    val hashContrasena = varchar("hash_contrasena", 255)
    val secreta2fa = varchar("secreta_2fa", 100)
    val region = varchar("region", 5).default("MX")
    val fechaCreacion = timestamp("fecha_creacion").nullable()
    val fechaActualizacion = timestamp("fecha_actualizacion").nullable()

    override val primaryKey = PrimaryKey(id)
}

object IngresosTable : Table("ingresos") {
    val id = uuid("id")
    val usuarioId = uuid("usuario_id").references(UsuariosTable.id)
    val montoNeto = decimal("monto_neto", 12, 2)
    val esAuditado = bool("es_auditado").default(false)
    val fechaRegistro = timestamp("fecha_registro").nullable()

    override val primaryKey = PrimaryKey(id)
}

object AsesoriaFinancieraTable : Table("asesoria_financiera") {
    val id = uuid("id")
    val usuarioId = uuid("usuario_id").references(UsuariosTable.id)
    val asesoriaTexto = text("asesoria_texto")
    val hashAsesoria = varchar("hash_asesoria", 64)
    val fechaActualizacion = timestamp("fecha_actualizacion").nullable()

    override val primaryKey = PrimaryKey(id)
}

object CategoriasTable : Table("categorias") {
    val id = uuid("id")
    val nombre = varchar("nombre", 100).uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}

object ResumenGastosTable : Table("resumen_gastos") {
    val id = uuid("id")
    val usuarioId = uuid("usuario_id").references(UsuariosTable.id)
    val periodoAnio = integer("periodo_anio")
    val periodoMes = integer("periodo_mes")
    val totalGastadoMxn = decimal("total_gastado_mxn", 12, 2).default(BigDecimal.ZERO)
    val fechaActualizacion = timestamp("fecha_actualizacion").nullable()

    override val primaryKey = PrimaryKey(id)
}

object TarjetasCreditoTable : Table("tarjetas_credito") {
    val id = uuid("id")
    val usuarioId = uuid("usuario_id").references(UsuariosTable.id)
    val banco = varchar("nombre_banco", 100)
    val alias = varchar("alias_tarjeta", 150)
    val ultimosCuatro = varchar("ultimos_cuatro_digitos", 4).nullable()
    val fechaRegistro = timestamp("fecha_registro").nullable()

    override val primaryKey = PrimaryKey(id)
}

object TransaccionesTable : Table("transacciones") {
    val id = uuid("id")
    val tarjetaId = uuid("tarjeta_id").references(TarjetasCreditoTable.id)
    val categoriaId = uuid("categoria_id").references(CategoriasTable.id).nullable()
    val fechaTransaccion = date("fecha_transaccion")
    val descripcion = varchar("descripcion", 255)
    val montoMxn = decimal("monto_mxn", 12, 2)
    val monedaOriginal = varchar("moneda_original", 10).default("MXN")
    val fechaRegistro = timestamp("fecha_registro").nullable()

    override val primaryKey = PrimaryKey(id)
}
