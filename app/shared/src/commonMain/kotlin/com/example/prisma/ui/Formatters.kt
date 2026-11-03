package com.example.prisma.ui

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Formatea un monto decimal en texto ("15000.5", "15000.50") como pesos: "$15,000.50".
 * Trabaja sobre el texto para no perder centavos por redondeo de Double.
 */
fun formatMxn(amount: String): String {
    val negative = amount.startsWith('-')
    val parts = amount.removePrefix("-").split('.')
    val intPart = parts[0].trimStart('0').ifEmpty { "0" }
    val decPart = parts.getOrNull(1).orEmpty().padEnd(2, '0').take(2)
    val grouped = intPart.reversed().chunked(3).joinToString(",").reversed()
    return (if (negative) "-" else "") + "\$$grouped.$decPart"
}

/** "2026-11-02T20:03" -> "02/11/2026 · 20:03". Si el formato no coincide, devuelve el texto original. */
fun formatIsoDateTime(iso: String): String {
    val date = iso.substringBefore('T').split('-')
    if (date.size != 3) return iso
    val time = iso.substringAfter('T', "")
    return "${date[2]}/${date[1]}/${date[0]}" + if (time.isNotEmpty()) " · $time" else ""
}

/**
 * Convierte milisegundos UTC (como los que da el DatePicker) a "yyyy-MM-dd".
 *
 * Usa el algoritmo civil_from_days de Howard Hinnant con división hacia abajo, válido para
 * cualquier fecha (antes y después de 1970). Reemplaza a la versión anterior, que generaba
 * fechas inexistentes (p. ej. "1902-02-29") y el servidor las rechazaba.
 */
fun epochMillisToIsoDate(millis: Long): String {
    val z = millis.floorDiv(86_400_000L) + 719_468L
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097L                                   // [0, 146096]
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365  // [0, 399]
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)              // [0, 365]
    val mp = (5 * doy + 2) / 153                                   // [0, 11], marzo = 0
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + if (month <= 2) 1 else 0

    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

/** Fecha de hoy (UTC) en formato "yyyy-MM-dd"; las fechas ISO se pueden comparar como texto. */
@OptIn(ExperimentalTime::class)
fun todayIsoDate(): String = epochMillisToIsoDate(Clock.System.now().toEpochMilliseconds())
