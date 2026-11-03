package com.example.prisma.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.math.BigDecimal

// Las fechas de registro/actualización usan el DEFAULT CURRENT_TIMESTAMP de PostgreSQL.
// Sin defaultExpression, Exposed enviaba NULL explícito en el INSERT y anulaba ese DEFAULT.
object UsuariosTable : Table("usuarios") {
    val id = uuid("id")
    val correoElectronico = varchar("correo_electronico", 255).uniqueIndex()
    val hashContrasena = varchar("hash_contrasena", 255)
    val secreta2fa = varchar("secreta_2fa", 100)
    val region = varchar("region", 5).default("MX")
    val fechaCreacion = timestamp("fecha_creacion").defaultExpression(CurrentTimestamp).nullable()
    val fechaActualizacion = timestamp("fecha_actualizacion").defaultExpression(CurrentTimestamp).nullable()

    override val primaryKey = PrimaryKey(id)
}

object DatosPersonalesTable : Table("datos_personales") {
    val id = integer("id").autoIncrement()
    val usuarioId = uuid("usuario_id").references(UsuariosTable.id)
    val nombre = varchar("nombre", 100)
    val apellido = varchar("apellido", 100)
    val fechaNacimiento = date("fecha_nacimiento").nullable()
    val fechaCreacion = timestamp("fecha_creacion").defaultExpression(CurrentTimestamp).nullable()

    override val primaryKey = PrimaryKey(id)
}

object IngresosTable : Table("ingresos") {
    val id = uuid("id")
    val usuarioId = uuid("usuario_id").references(UsuariosTable.id)
    val montoNeto = decimal("monto_neto", 12, 2)
    /** Concepto del ingreso (migración 001_ingresos_nombre.sql). */
    val nombre = varchar("nombre", 100)
    val esAuditado = bool("es_auditado").default(false)
    val fechaRegistro = timestamp("fecha_registro").defaultExpression(CurrentTimestamp).nullable()

    override val primaryKey = PrimaryKey(id)
}

object AsesoriaFinancieraTable : Table("asesoria_financiera") {
    val id = uuid("id")
    val usuarioId = uuid("usuario_id").references(UsuariosTable.id)
    val asesoriaTexto = text("asesoria_texto")
    val hashAsesoria = varchar("hash_asesoria", 64)
    val fechaActualizacion = timestamp("fecha_actualizacion").defaultExpression(CurrentTimestamp).nullable()

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
    val fechaActualizacion = timestamp("fecha_actualizacion").defaultExpression(CurrentTimestamp).nullable()

    override val primaryKey = PrimaryKey(id)
}

object TarjetasCreditoTable : Table("tarjetas_credito") {
    val id = uuid("id")
    val usuarioId = uuid("usuario_id").references(UsuariosTable.id)
    val banco = varchar("nombre_banco", 100)
    val alias = varchar("alias_tarjeta", 150)
    val ultimosCuatro = varchar("ultimos_cuatro_digitos", 4).nullable()
    val fechaRegistro = timestamp("fecha_registro").defaultExpression(CurrentTimestamp).nullable()

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
    val fechaRegistro = timestamp("fecha_registro").defaultExpression(CurrentTimestamp).nullable()

    override val primaryKey = PrimaryKey(id)
}

/** Dispositivos con acceso biométrico (migración 002_dispositivos_biometricos.sql). */
object DispositivosBiometricosTable : Table("dispositivos_biometricos") {
    val id = uuid("id")
    val usuarioId = uuid("usuario_id").references(UsuariosTable.id)
    /** SHA-256 (hex) del token del dispositivo; el token en claro solo existe en el teléfono. */
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val plataforma = varchar("plataforma", 20)
    /** Hora del último código 2FA válido: el acceso biométrico vale 24 h desde aquí. */
    val ultimo2fa = timestamp("ultimo_2fa")
    val ultimoUso = timestamp("ultimo_uso").nullable()
    val fechaCreacion = timestamp("fecha_creacion").defaultExpression(CurrentTimestamp).nullable()

    override val primaryKey = PrimaryKey(id)
}
