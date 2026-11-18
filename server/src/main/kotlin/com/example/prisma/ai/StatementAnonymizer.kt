package com.example.prisma.ai

import java.text.Normalizer

/**
 * Quita datos personales del texto de un estado de cuenta antes de enviarlo a la IA
 * (consentimiento EnvioIa: "documentos ya anonimizados").
 *
 * Se eliminan: nombre del titular, correos, CURP, RFC, números de tarjeta/cuenta/CLABE,
 * teléfonos y renglones de domicilio. Los comercios, fechas y montos se conservan porque
 * son lo que se analiza.
 */
object StatementAnonymizer {

    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val curp = Regex("\\b[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z0-9]\\d\\b", RegexOption.IGNORE_CASE)
    private val rfc = Regex("\\b[A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3}\\b", RegexOption.IGNORE_CASE)

    /** 8 o más dígitos seguidos (con espacios o guiones): tarjeta, cuenta, CLABE, referencia. */
    // No toca importes ("12345678.90"): el número no puede ir seguido de punto o coma decimal.
    private val longNumber = Regex("(?<![\\d.,$])\\d(?:[ -]?\\d){7,}(?!\\d|[.,]\\d)")
    private val phone = Regex("(?<!\\d)(?:\\+?52[ -]?)?\\(?\\d{2,3}\\)?[ -]\\d{3,4}[ -]\\d{4}(?!\\d)")

    private val addressLine = Regex(
        "(C\\.?\\s?P\\.?\\s*\\d{5}|C[OÓ]DIGO POSTAL|\\bCOL(ONIA)?\\.?\\s|DOMICILIO|\\bDELEG\\.|ALCALD[IÍ]A|MUNICIPIO)",
        RegexOption.IGNORE_CASE,
    )

    /**
     * @param holderNames nombre(s) y apellido(s) del titular, tal como están registrados en Prisma.
     */
    fun anonymize(text: String, holderNames: List<String> = emptyList()): String {
        val names = holderNames
            .flatMap { it.split(Regex("\\s+")) }
            .map { it.trim() }
            .filter { it.length >= 3 }
            .distinct()

        return text.lineSequence().joinToString("\n") { raw ->
            if (addressLine.containsMatchIn(raw)) return@joinToString "[DOMICILIO]"

            var line = raw
            line = email.replace(line, "[CORREO]")
            line = curp.replace(line, "[CURP]")
            line = rfc.replace(line, "[RFC]")
            line = phone.replace(line, "[TELÉFONO]")
            line = longNumber.replace(line, "[NÚMERO]")
            names.forEach { name -> line = replaceWord(line, name) }
            line
        }
    }

    /** Reemplaza una palabra del nombre sin importar mayúsculas ni acentos. */
    private fun replaceWord(line: String, word: String): String {
        val plainLine = strip(line)
        val plainWord = strip(word)
        val regex = Regex("(?<![\\p{L}])" + Regex.escape(plainWord) + "(?![\\p{L}])", RegexOption.IGNORE_CASE)
        val matches = regex.findAll(plainLine).toList()
        if (matches.isEmpty()) return line
        // strip() conserva la longitud, así que los índices valen en el texto original.
        val out = StringBuilder(line)
        matches.asReversed().forEach { out.replace(it.range.first, it.range.last + 1, "[TITULAR]") }
        return out.toString()
    }

    /** Quita acentos conservando un carácter por carácter (misma longitud). */
    private fun strip(value: String): String =
        value.map { c ->
            Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
                .firstOrNull { Character.getType(it) != Character.NON_SPACING_MARK.toInt() } ?: c
        }.joinToString("")
}
