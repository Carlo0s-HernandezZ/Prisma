package com.example.prisma.http

/**
 * Validación por **lista blanca** de archivos subidos (estados de cuenta de tarjetas).
 *
 * No se confía en el nombre ni en el Content-Type que manda el cliente: el tipo se decide por los
 * primeros bytes reales del archivo ("magic bytes") y debe coincidir con la lista permitida.
 */
object UploadValidation {
    /** 10 MB: suficiente para un estado de cuenta en PDF o una foto. */
    const val MAX_FILE_BYTES = 10 * 1024 * 1024

    private const val MAX_FILE_NAME = 120

    /** Tipos permitidos → extensión que se fuerza en el nombre guardado. */
    private val ALLOWED = mapOf(
        "application/pdf" to "pdf",
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp",
        "image/heic" to "heic",
    )

    /** Solo letras (con acentos), números, espacio, punto, guion, guion bajo y paréntesis. */
    private val SAFE_NAME_CHARS = Regex("[^\\p{L}\\p{N} ._()\\-]")

    /** Detecta el tipo real del archivo por su firma; null si no está en la lista blanca. */
    fun detectMime(bytes: ByteArray): String? {
        fun startsWith(vararg sig: Int) = bytes.size >= sig.size && sig.indices.all { bytes[it] == sig[it].toByte() }
        fun asciiAt(offset: Int, text: String) =
            bytes.size >= offset + text.length && text.indices.all { bytes[offset + it] == text[it].code.toByte() }
        return when {
            asciiAt(0, "%PDF-") -> "application/pdf"
            startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
            asciiAt(0, "RIFF") && asciiAt(8, "WEBP") -> "image/webp"
            asciiAt(4, "ftyp") && (asciiAt(8, "heic") || asciiAt(8, "heix") || asciiAt(8, "mif1")) -> "image/heic"
            else -> null
        }
    }

    /**
     * Nombre seguro para guardar y mostrar: sin rutas (`../`), sin caracteres de control ni HTML
     * (`<`, `>`, comillas), longitud limitada y con la extensión que corresponde al tipo real.
     */
    fun safeFileName(original: String?, mime: String): String {
        val ext = ALLOWED.getValue(mime)
        val base = original.orEmpty()
            .substringAfterLast('/').substringAfterLast('\\')
            .substringBeforeLast('.')
            .replace(SAFE_NAME_CHARS, "_")
            .trim(' ', '.', '_')
            .take(MAX_FILE_NAME - ext.length - 1)
            .ifEmpty { "documento" }
        return "$base.$ext"
    }
}
