package com.example.prisma.ui

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
