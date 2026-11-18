package com.example.prisma.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Un análisis por tarjeta y mes (migración 005_analisis_ia.sql; DatabaseFactory la crea si no existe).
 * Volver a subir el estado del mismo mes reemplaza el análisis anterior y sus movimientos.
 */
object AnalisisEstadosTable : Table("analisis_estados_cuenta") {
    val id = uuid("id")
    val usuarioId = uuid("usuario_id").references(UsuariosTable.id, onDelete = ReferenceOption.CASCADE)
    val tarjetaId = uuid("tarjeta_id").references(TarjetasCreditoTable.id, onDelete = ReferenceOption.CASCADE)
    /** Mes del estado de cuenta, "AAAA-MM". */
    val periodo = varchar("periodo", 7)
    /** SHA-256 de los archivos: evita volver a enviar a la IA el mismo documento. */
    val huella = varchar("huella", 64)
    val fuente = varchar("fuente", 10)
    /** StatementAnalysisDto en JSON, cifrado con AES-256-GCM (DataCipher): contiene comercios y montos. */
    val resultado = text("resultado")
    val deudaTotal = decimal("deuda_total", 12, 2)
    val totalGastos = decimal("total_gastos", 12, 2)
    val totalHormiga = decimal("total_hormiga", 12, 2)
    val fechaAnalisis = timestamp("fecha_analisis").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(tarjetaId, periodo)
    }
}

/**
 * Movimientos leídos de cada estado: alimentan los dashboards mes con mes.
 * Monto, fecha y categoría quedan en claro para poder sumarlos; la descripción va cifrada.
 */
object MovimientosAnalizadosTable : Table("movimientos_analizados") {
    val id = uuid("id")
    val analisisId = uuid("analisis_id").references(AnalisisEstadosTable.id, onDelete = ReferenceOption.CASCADE)
    val usuarioId = uuid("usuario_id").references(UsuariosTable.id, onDelete = ReferenceOption.CASCADE)
    val tarjetaId = uuid("tarjeta_id").references(TarjetasCreditoTable.id, onDelete = ReferenceOption.CASCADE)
    /** Mes del estado al que pertenece el movimiento, "AAAA-MM". */
    val periodo = varchar("periodo", 7)
    val fecha = date("fecha").nullable()
    val descripcion = text("descripcion")
    val categoria = varchar("categoria", 40)
    /** COMPRA, CARGO_FINANCIERO, PAGO o DEVOLUCION. */
    val tipo = varchar("tipo", 20)
    val monto = decimal("monto", 12, 2)
    val esHormiga = bool("es_hormiga").default(false)
    val sospechoso = bool("sospechoso").default(false)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, usuarioId, periodo)
    }
}

/** Consejo del día por usuario: se pide a Gemini una sola vez al día. */
object ConsejosDiariosTable : Table("consejos_diarios") {
    val usuarioId = uuid("usuario_id").references(UsuariosTable.id, onDelete = ReferenceOption.CASCADE)
    val fecha = date("fecha")
    val titulo = varchar("titulo", 120)
    val texto = text("texto")
    val fuenteId = varchar("fuente_id", 30)

    override val primaryKey = PrimaryKey(usuarioId, fecha)
}

/** Auditorías de nómina (migración 006_nomina_recordatorios.sql). El resultado va cifrado. */
object AuditoriasNominaTable : Table("auditorias_nomina") {
    val id = uuid("id")
    val usuarioId = uuid("usuario_id").references(UsuariosTable.id, onDelete = ReferenceOption.CASCADE)
    /** Mes de la nómina, "AAAA-MM". */
    val periodo = varchar("periodo", 7)
    /** Fecha de pago (o inicio del periodo) para ordenar y comparar con la nómina anterior. */
    val fechaReferencia = varchar("fecha_referencia", 10)
    val fuente = varchar("fuente", 5)
    val huella = varchar("huella", 64)
    val resultado = text("resultado")
    val neto = decimal("neto", 12, 2)
    val alertas = integer("alertas")
    val fechaAnalisis = timestamp("fecha_analisis").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(usuarioId, huella)
    }
}

/** Recordatorios que programa el usuario para subir su nómina o un estado de cuenta. */
object RecordatoriosTable : Table("recordatorios") {
    val id = uuid("id")
    val usuarioId = uuid("usuario_id").references(UsuariosTable.id, onDelete = ReferenceOption.CASCADE)
    val tipo = varchar("tipo", 20)
    val tarjetaId = uuid("tarjeta_id").references(TarjetasCreditoTable.id, onDelete = ReferenceOption.CASCADE).nullable()
    val titulo = varchar("titulo", 80)
    val frecuencia = varchar("frecuencia", 12)
    val dia = integer("dia").nullable()
    val dia2 = integer("dia2").nullable()
    val fechaInicio = varchar("fecha_inicio", 10).nullable()
    val hora = varchar("hora", 5)
    val activo = bool("activo").default(true)
    val fechaCreacion = timestamp("fecha_creacion").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}
