
package com.example.prisma.ai

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

// Resultado de la extracción
data class PayrollPdfData(
    val textoExtraido: String,
    val numeroPaginas: Int,
    val caracteresExtraidos: Int,
    val requiereOcr: Boolean
)

class PayrollPdfParser {

    companion object {
        private const val MAX_PDF_BYTES = 10 * 1024 * 1024
        private const val MAX_PAGINAS = 20
        private const val MAX_CARACTERES = 100_000
    }

    fun extraer(pdf: ByteArray): PayrollPdfData {

        require(pdf.isNotEmpty()) {
            "El archivo PDF está vacío."
        }

        require(pdf.size <= MAX_PDF_BYTES) {
            "El PDF supera los 10 MB permitidos."
        }

        require(
            pdf.size >= 5 &&
                    pdf.copyOfRange(0, 5)
                        .decodeToString() == "%PDF-"
        ) {
            "El documento no tiene una cabecera PDF válida."
        }

        return Loader.loadPDF(pdf).use { documento ->

            require(!documento.isEncrypted) {
                "No se admiten PDF protegidos con contraseña."
            }

            val paginas = documento.numberOfPages

            require(paginas in 1..MAX_PAGINAS) {
                "El PDF debe contener entre 1 y 20 páginas."
            }

            val extractor = PDFTextStripper().apply {
                sortByPosition = true
                startPage = 1
                endPage = paginas
            }

            val texto = extractor
                .getText(documento)
                .trim()

            require(texto.length <= MAX_CARACTERES) {
                "El documento contiene demasiado texto."
            }

            PayrollPdfData(
                textoExtraido = texto,
                numeroPaginas = paginas,
                caracteresExtraidos = texto.length,
                requiereOcr = texto.isBlank()
            )
        }
    }
}
