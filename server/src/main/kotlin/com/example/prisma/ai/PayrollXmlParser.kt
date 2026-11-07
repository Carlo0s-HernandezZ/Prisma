
package com.example.prisma.ai

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.RoundingMode
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

data class PayrollXmlData(
    val periodo: String,
    val fechaPago: String,
    val percepciones: BigDecimal,
    val deducciones: BigDecimal,
    val otrosPagos: BigDecimal,
    val isr: BigDecimal,
    val seguridadSocial: BigDecimal,
    val netoCfdi: BigDecimal
)

class PayrollXmlParser {

    companion object {
        private const val CFDI_NS =
            "http://www.sat.gob.mx/cfd/4"

        private const val NOMINA_NS =
            "http://www.sat.gob.mx/nomina12"

        private const val MAX_XML_BYTES =
            10 * 1024 * 1024
    }

    fun extraer(xml: ByteArray): PayrollXmlData {

        require(xml.isNotEmpty()) {
            "El XML está vacío."
        }

        require(xml.size <= MAX_XML_BYTES) {
            "El XML supera el tamaño permitido."
        }

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true

            setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
            )

            setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false
            )

            setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false
            )

            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)

            setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false
            )

            setXIncludeAware(false)
            isExpandEntityReferences = false

            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }

        val documento = ByteArrayInputStream(xml).use { entrada ->
            factory.newDocumentBuilder().parse(entrada)
        }

        val comprobante = documento.documentElement

        require(
            comprobante.localName == "Comprobante" &&
                    comprobante.namespaceURI == CFDI_NS
        ) {
            "No es un CFDI 4.0 reconocido."
        }

        require(
            comprobante.getAttribute("TipoDeComprobante") == "N"
        ) {
            "El CFDI no corresponde a una nómina."
        }

        val complementos = comprobante
            .getElementsByTagNameNS(CFDI_NS, "Complemento")

        require(complementos.length == 1) {
            "No se encontró un complemento CFDI único."
        }

        val complemento = complementos.item(0) as Element

        val nominas = complemento
            .getElementsByTagNameNS(NOMINA_NS, "Nomina")

        require(nominas.length == 1) {
            "No se encontró un complemento de nómina único."
        }

        val nomina = nominas.item(0) as Element

        require(nomina.getAttribute("Version") == "1.2") {
            "Versión del complemento no compatible."
        }

        val fechaPago = nomina.getAttribute("FechaPago")

        require(
            Regex("""\d{4}-\d{2}-\d{2}""").matches(fechaPago)
        ) {
            "La fecha de pago no tiene formato válido."
        }

        val percepciones = importe(
            nomina, "TotalPercepciones"
        )

        val deducciones = importe(
            nomina, "TotalDeducciones"
        )

        val otrosPagos = importe(
            nomina, "TotalOtrosPagos"
        )

        val netoCfdi = importe(
            comprobante, "Total",
            obligatorio = true
        )

        var isr = BigDecimal.ZERO
        var seguridadSocial = BigDecimal.ZERO

        val conceptos = nomina.getElementsByTagNameNS(
            NOMINA_NS,
            "Deduccion"
        )

        for (i in 0 until conceptos.length) {
            val concepto = conceptos.item(i) as Element

            val tipo = concepto.getAttribute("TipoDeduccion")
            val monto = importe(
                concepto, "Importe",
                obligatorio = true
            )

            when (tipo) {
                "001" -> seguridadSocial += monto
                "002" -> isr += monto
            }
        }

        val fechaInicioPago = nomina
            .getAttribute("FechaInicialPago")

        val periodoNomina = if (
            Regex("""\d{4}-\d{2}-\d{2}""")
                .matches(fechaInicioPago)
        ) {
            fechaInicioPago.substring(0, 7)
        } else {
            fechaPago.substring(0, 7)
        }

        return PayrollXmlData(
            periodo = periodoNomina,
            fechaPago = fechaPago,
            percepciones = percepciones,
            deducciones = deducciones,
            otrosPagos = otrosPagos,
            isr = isr,
            seguridadSocial = seguridadSocial,
            netoCfdi = netoCfdi
        )
    }

    private fun importe(
        elemento: Element,
        atributo: String,
        obligatorio: Boolean = false
    ): BigDecimal {

        val texto = elemento.getAttribute(atributo)

        if (texto.isBlank()) {
            require(!obligatorio) {
                "Falta el importe obligatorio: $atributo."
            }
            return BigDecimal.ZERO.setScale(2)
        }

        val cantidad = texto.toBigDecimalOrNull()

        require(
            cantidad != null &&
                    cantidad.signum() >= 0 &&
                    cantidad.scale() <= 2
        ) {
            "Importe inválido en $atributo."
        }

        return cantidad.setScale(
            2, RoundingMode.UNNECESSARY
        )
    }
}
