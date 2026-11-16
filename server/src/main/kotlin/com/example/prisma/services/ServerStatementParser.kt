package com.example.prisma.services

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

data class MsiParsedItem(
    val descripcion: String,
    val montoOriginal: String,
    val saldoPendiente: String,
    val pagoRequerido: String,
    val pagoActual: Int,
    val totalMeses: Int,
    val mesesRestantes: Int,
)

data class RegularItem(
    val fecha: String,
    val descripcion: String,
    val monto: String,
)

data class ParsedStatementData(
    val montoPesos: String?,
    val totalAbonos: String?,
    val periodo: String? = null,
    val fechaCorte: String? = null,
    val fechaPago: String? = null,
    val msiItems: List<MsiParsedItem> = emptyList(),
    val regularItems: List<RegularItem> = emptyList(),
    val rawTextSummary: String?,
)

object ServerStatementParser {
    fun parsePdfBytes(bytes: ByteArray): ParsedStatementData {
        var totalCargos: String? = null
        var totalAbonos: String? = null
        var periodo: String? = null
        var fechaCorte: String? = null
        var fechaPago: String? = null
        val msiList = mutableListOf<MsiParsedItem>()
        val regularList = mutableListOf<RegularItem>()
        var rawSummary: String? = null

        try {
            if (bytes.isNotEmpty()) {
                val document = Loader.loadPDF(bytes)
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                document.close()

                if (text.isNotBlank()) {
                    rawSummary = text

                    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    
                    // Extraer Totales
                    for (i in lines.indices) {
                        val line = lines[i]
                        if (line.contains("Total cargos", ignoreCase = true)) {
                            totalCargos = extractAmount(line) ?: (if (i + 1 < lines.size) extractAmount(lines[i + 1]) else null)
                        }
                        if (line.contains("Total abonos", ignoreCase = true)) {
                            totalAbonos = extractAmount(line) ?: (if (i + 1 < lines.size) extractAmount(lines[i + 1]) else null)
                        }
                    }

                    // Extraer MSI (patrón "X de Y" para número de pagos, ej. "4 de 15")
                    val msiRegex = Regex("""(\d+)\s+de\s+(\d+)""", RegexOption.IGNORE_CASE)
                    for (i in lines.indices) {
                        val line = lines[i]
                        val match = msiRegex.find(line)
                        if (match != null && (line.contains("AMAZON", ignoreCase = true) || line.contains("MESES", ignoreCase = true))) {
                            val pagoActual = match.groupValues[1].toIntOrNull() ?: 1
                            val totalMeses = match.groupValues[2].toIntOrNull() ?: 1
                            val mesesRestantes = maxOf(0, totalMeses - pagoActual)

                            val amounts = mutableListOf<String>()
                            for (j in maxOf(0, i - 1)..minOf(lines.size - 1, i + 3)) {
                                extractAmount(lines[j])?.let { amounts.add(it) }
                            }

                            val montoOrig = amounts.getOrNull(0) ?: "0.00"
                            val saldoPend = amounts.getOrNull(1) ?: "0.00"
                            val pagoReq = amounts.getOrNull(2) ?: "0.00"

                            msiList.add(
                                MsiParsedItem(
                                    descripcion = line,
                                    montoOriginal = montoOrig,
                                    saldoPendiente = saldoPend,
                                    pagoRequerido = pagoReq,
                                    pagoActual = pagoActual,
                                    totalMeses = totalMeses,
                                    mesesRestantes = mesesRestantes,
                                )
                            )
                        }
                    }

                    if (totalCargos == null) {
                        val allAmounts = Regex("""\$([0-9,]+\.[0-9]{2})""").findAll(text).map { it.groupValues[1].replace(",", "") }.toList()
                        if (allAmounts.isNotEmpty()) {
                            totalCargos = allAmounts.last()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ParsedStatementData(
            montoPesos = totalCargos,
            totalAbonos = totalAbonos,
            periodo = periodo,
            fechaCorte = fechaCorte,
            fechaPago = fechaPago,
            msiItems = msiList,
            regularItems = regularList,
            rawTextSummary = rawSummary,
        )
    }

    private fun extractAmount(text: String): String? {
        val match = Regex("""\$?([0-9]{1,3}(?:,[0-9]{3})*\.[0-9]{2})""").find(text)
        return match?.groupValues?.get(1)?.replace(",", "")
    }
}
