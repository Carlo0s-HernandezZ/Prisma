package com.example.prisma.ai

import com.example.prisma.api.AlertSeverity
import com.example.prisma.api.CategoryAmountDto
import com.example.prisma.api.ChargeAlertDto
import com.example.prisma.api.DebtItemDto
import com.example.prisma.api.HormigaMerchantDto
import com.example.prisma.api.HormigaSummaryDto
import com.example.prisma.api.InstallmentPurchaseDto
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class MovementType { COMPRA, CARGO_FINANCIERO, PAGO, DEVOLUCION }

/** Un movimiento del estado de cuenta. El monto siempre es positivo; el tipo dice si suma o resta. */
data class Movement(
    val fecha: LocalDate?,
    val descripcion: String,
    val monto: BigDecimal,
    val tipo: MovementType,
    val categoria: String,
    val esHormiga: Boolean = false,
    val sospechoso: Boolean = false,
)

internal fun BigDecimal.money(): BigDecimal = setScale(2, RoundingMode.HALF_UP)
internal fun BigDecimal.plain(): String = money().toPlainString()
internal val ZERO: BigDecimal = BigDecimal.ZERO.money()

/** Convierte "$1,234.50", "1234.5" o un número en BigDecimal; null si no es un monto válido. */
internal fun parseMoney(raw: String?): BigDecimal? {
    val cleaned = raw?.replace(Regex("[\\s$,]|MXN|M\\.N\\.", RegexOption.IGNORE_CASE), "")
        ?.removeSuffix("-")?.removePrefix("+")
        ?.takeIf { it.isNotBlank() } ?: return null
    return cleaned.toBigDecimalOrNull()?.abs()?.takeIf { it < BigDecimal("100000000") }?.money()
}

internal fun normalizeText(value: String): String =
    Normalizer.normalize(value.uppercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

/** Categorías de gasto que entienden la IA, el lector local y los dashboards. */
object Categorizer {
    const val SUPERMERCADO = "Supermercado"
    const val RESTAURANTES = "Restaurantes y cafeterías"
    const val ANTOJOS = "Comida rápida y antojos"
    const val CONVENIENCIA = "Tiendas de conveniencia"
    const val TRANSPORTE = "Transporte y gasolina"
    const val SERVICIOS = "Servicios y suscripciones"
    const val SALUD = "Salud"
    const val EDUCACION = "Educación"
    const val ROPA = "Ropa y departamentales"
    const val ENTRETENIMIENTO = "Entretenimiento"
    const val VIAJES = "Viajes"
    const val EN_LINEA = "Compras en línea"
    const val HOGAR = "Hogar"
    const val MASCOTAS = "Mascotas"
    const val TRAMITES = "Trámites e impuestos"
    const val INTERESES = "Intereses y comisiones"
    const val MENSUALIDADES = "Mensualidades a meses"
    const val OTROS = "Otros"

    val all = listOf(
        SUPERMERCADO, RESTAURANTES, ANTOJOS, CONVENIENCIA, TRANSPORTE, SERVICIOS, SALUD, EDUCACION,
        ROPA, ENTRETENIMIENTO, VIAJES, EN_LINEA, HOGAR, MASCOTAS, TRAMITES, INTERESES, OTROS,
    )

    /** Categorías donde suelen estar los gastos hormiga. */
    val hormigaCategories = setOf(RESTAURANTES, ANTOJOS, CONVENIENCIA, ENTRETENIMIENTO, SERVICIOS)

    private val rules: List<Pair<String, Regex>> = listOf(
        INTERESES to "INTERES|COMISION|IVA |I\\.V\\.A|ANUALIDAD|CARGO POR|PENALIZA|GASTOS DE COBRANZA|MORATORIO",
        CONVENIENCIA to "OXXO|7 ?-?ELEVEN|SEVEN ELEVEN|CIRCULO K|EXTRA |KIOSKO|FARMACIAS? DEL AHORRO EXPRESS|SUPER ?K",
        ANTOJOS to "NAYAX|ELOTE|GONG ?CHA|DULC|BOBA|\\bTEA\\b|FRAPP|STARBUCKS|CAFE|COFFEE|DUNKIN|KRISPY|MCDONALD|BURGER|KFC|DOMINO|PIZZA|SUBWAY|CARL'?S|TACO|TORTA|" +
            "HELADO|NIEVE|PALETERIA|DULCER|PANADERIA|SNACK|CHURRER|ANTOJ|LITTLE CAESAR|WINGS|POPEYES",
        RESTAURANTES to "RESTAUR|REST |VIPS|SANBORNS CAFE|TOKS|ITALIANNI|CHILI'?S|APPLEBEE|BAR |CANTINA|MARISCOS|COCINA",
        SUPERMERCADO to "WAL ?-?MART|ABARROTE|MINISUPER|CARNICERIA|FRUTERIA|TORTILLERIA|WALMART|WAL-MART|BODEGA AURRERA|AURRERA|SORIANA|CHEDRAUI|LA COMER|CITY MARKET|FRESKO|HEB|H-E-B|" +
            "SUPERAMA|COSTCO|SAMS|SAM'S|CASA LEY|CALIMAX|ALSUPER|SMART|MERCADO|SUPER",
        TRANSPORTE to "PETROL|AUTOSERV|GASOLINERA|AUTOZONE|REFACCION|LLANTA|VERIFICENTRO|UBER(?! ?EATS)|DIDI(?! ?FOOD)|CABIFY|PEMEX|GASOL|GAS |SHELL|MOBIL|BP |G500|OXXO GAS|TOTAL ENERG|" +
            "ESTACIONAMIENTO|PARKING|CASETA|IAVE|PASE |TAG |METRO|AUTOBUS|ADO |ETN|PRIMERA PLUS",
        SERVICIOS to "ITUNES|APPLE|NETFLIX|SPOTIFY|DISNEY|HBO|MAX\\.COM|PRIME VIDEO|AMAZON PRIME|APPLE\\.COM|ICLOUD|GOOGLE ?\\*|" +
            "YOUTUBE|TELCEL|AT&T|ATT |MOVISTAR|TELMEX|IZZI|TOTALPLAY|MEGACABLE|CFE|AGUA|SIAPA|SACMEX|GAS NATURAL|" +
            "NATURGY|MICROSOFT|XBOX|PLAYSTATION|STEAM|CHATGPT|OPENAI|SUSCRIP|MEMBRES|SMARTFIT|GYM|GIMNASIO",
        SALUD to "FARM\\b|FARMA|\\bLAB\\b|ANALISIS CLINIC|FARMACIA|FARMACIAS|BENAVIDES|GUADALAJARA FARM|SIMILARES|SAN PABLO|HOSPITAL|CLINICA|MEDIC|DOCTOR|" +
            "LABORATORIO|DENTAL|OPTICA|SALUD",
        EDUCACION to "COLEGIO|ESCUELA|UNIVERSIDAD|UNIV |INSTITUTO|LIBRERIA|GANDHI|SANBORNS LIBR|PAPELERIA|OFFICE DEPOT|" +
            "OFFICEMAX|COURSERA|UDEMY|PLATZI",
        ROPA to "\\bITX\\b|LEFTIES|STRADIVARIUS|OYSHO|MASSIMO|ZAPATER|LIVERPOOL|PALACIO DE HIERRO|SUBURBIA|SEARS|COPPEL|ZARA|BERSHKA|PULL|H&M|C&A|SHEIN|NIKE|ADIDAS|" +
            "INNOVASPORT|MARTI|PRICE SHOES|ANDREA|FLEXI|SANBORNS",
        ENTRETENIMIENTO to "SOLUTICKET|PASSLINE|ETICKET|BOWL|CINE|BILLAR|CINEPOLIS|CINEMEX|TICKETMASTER|BOLETO|EVENTO|CONCIERTO|ARENA|BOLICHE|SIX FLAGS|" +
            "KIDZANIA|MUSEO|TEATRO|JUEGO|GAME",
        VIAJES to "MOTEL|AEROMEXICO|VOLARIS|VIVA ?AEROBUS|INTERJET|HOTEL|AIRBNB|BOOKING|EXPEDIA|DESPEGAR|BEST DAY|" +
            "CITY EXPRESS|FIESTA INN|HOLIDAY INN|MARRIOTT|HILTON",
        EN_LINEA to "AMAZON|MERCADO ?LIBRE|MERCADOPAGO|MERCADO PAGO|MELI|PAYPAL|ALIEXPRESS|TEMU|EBAY|UBER ?EATS|" +
            "DIDI ?FOOD|RAPPI|SHOPIFY|STRIPE",
        HOGAR to "CERAMICA|TLAPALER|PINTURA|COMEX|HOME DEPOT|SODIMAC|ELEKTRA|BEST BUY|STEREN|IKEA|MUEBLER|FERRETER|TRUPER|LOWE|BED BATH",
        MASCOTAS to "PETCO|\\+?KOTA|VETERINAR|\\bVET\\b|MASC|PET ?SHOP|MASCOTA",
        TRAMITES to "SRIA|SECRETARIA|FINANZAS|TESORERIA|PREDIAL|TENENCIA|REFRENDO|\\bSAT\\b|GOBIERNO|\\bGOB\\b",
    ).map { (category, pattern) -> category to Regex(pattern) }

    fun categorize(description: String): String {
        val text = " " + normalizeText(description) + " "
        return rules.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.first ?: OTROS
    }

    /** Acepta la categoría de la IA solo si es una de las conocidas; si no, la calcula. */
    fun validOrGuess(category: String?, description: String): String {
        val match = all.firstOrNull { it.equals(category?.trim(), ignoreCase = true) }
        return if (match != null && match != OTROS) match else categorize(description)
    }
}

/**
 * Lector local de movimientos: reconoce renglones "fecha [fecha] descripción monto" de los estados
 * de los bancos mexicanos. Se usa cuando la IA no está disponible.
 */
object LocalMovementParser {

    private const val MONTHS = "ENE|FEB|MAR|ABR|MAY|JUN|JUL|AGO|SEPT?|OCT|NOV|DIC"
    private const val DATE =
        "(?:\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?|\\d{1,2}[ /-]?(?:$MONTHS)[A-Z]*(?:[ /-]?\\d{2,4})?)"

    private val row = Regex(
        "^\\s*($DATE)\\s+(?:$DATE\\s+)?(.+?)\\s+([+-]?)\\s*\\$?\\s*(\\d{1,3}(?:,\\d{3})*\\.\\d{2}|\\d+\\.\\d{2})\\s*(-|CR|ABONO)?\\s*$",
        RegexOption.IGNORE_CASE,
    )

    private val monthIndex = listOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC")

    private val skip = Regex(
        "SALDO|PAGO MINIMO|PAGO PARA NO GENERAR|LIMITE DE CREDITO|CREDITO DISPONIBLE|FECHA (DE )?CORTE|" +
            "FECHA LIMITE|TOTAL DE|SUBTOTAL|CAT |TASA",
        RegexOption.IGNORE_CASE,
    )

    fun parse(pages: List<String>, period: YearMonth?): List<Movement> =
        pages.flatMap { it.lines() }.mapNotNull { line -> parseLine(line, period) }

    internal fun parseLine(line: String, period: YearMonth?): Movement? {
        val match = row.find(line.trim()) ?: return null
        val (dateText, rawDescription, sign, amountText, suffix) = match.destructured
        val description = cleanDescription(rawDescription)
        if (description.length < 3 || skip.containsMatchIn(normalizeText(description))) return null
        val amount = parseMoney(amountText)?.takeIf { it.signum() > 0 } ?: return null

        val credit = sign == "-" || suffix.isNotBlank()
        val text = normalizeText(description)
        val type = when {
            Regex("SU PAGO|PAGO RECIBIDO|GRACIAS POR SU PAGO|PAGO TDC|PAGO DE TARJETA|ABONO|PAGO INTERBANCARIO|SPEI")
                .containsMatchIn(text) -> MovementType.PAGO
            Regex("DEVOL|REEMBOLSO|BONIFIC|CANCELACION|REVERSO").containsMatchIn(text) -> MovementType.DEVOLUCION
            credit -> MovementType.PAGO
            Regex("INTERES|COMISION|\\bIVA\\b|ANUALIDAD|CARGO POR|MORATORIO").containsMatchIn(text) ->
                MovementType.CARGO_FINANCIERO
            else -> MovementType.COMPRA
        }
        val category = when {
            type == MovementType.CARGO_FINANCIERO -> Categorizer.INTERESES
            // "AMAZON A MESES 002 de 024": el banco lista la mensualidad de una compra a meses como movimiento.
            type == MovementType.COMPRA && INSTALLMENT_MARK.containsMatchIn(description) -> Categorizer.MENSUALIDADES
            else -> Categorizer.categorize(description)
        }
        return Movement(parseDate(dateText, period), description.take(120), amount, type, category)
    }

    /** "2 de 24", "002 de 024": número de mensualidad de una compra a meses. */
    internal val INSTALLMENT_MARK = Regex("\\b\\d{1,3}\\s+de\\s+\\d{1,3}\\b", RegexOption.IGNORE_CASE)

    /** Quita ruido de la descripción: RFC del comercio, "| RFC: S.I.", "Compra en", espacios dobles. */
    internal fun cleanDescription(raw: String): String =
        raw.replace(Regex("\\|?\\s*RFC:?\\s*\\S+.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b[A-ZÑ&]{3,4}\\s?\\d{6}[A-Z0-9]{3}(?:MX)?\\b"), "")
            .replace(Regex("^Compra en\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

    private val numericDate = Regex("^(\\d{1,2})[-/](\\d{1,2})(?:[-/](\\d{2}|\\d{4}))?$")
    private val textDate = Regex("^(\\d{1,2})[ /-]?([A-Z]{3})[A-Z]*(?:[ /-]?(\\d{2}|\\d{4}))?$")

    /** "15/10/2026", "15-10-26", "15/10", "15 OCT 2026", "15OCT", "15-SEPT-26". */
    internal fun parseDate(text: String, period: YearMonth?): LocalDate? {
        val clean = normalizeText(text)
        val (day, month, yearText) = numericDate.find(clean)?.destructured?.let { (d, m, y) ->
            Triple(d.toInt(), m.toInt(), y)
        } ?: textDate.find(clean)?.destructured?.let { (d, m, y) ->
            val index = monthIndex.indexOf(m).takeIf { it >= 0 } ?: return null
            Triple(d.toInt(), index + 1, y)
        } ?: return null

        val year = when {
            yearText.isBlank() -> period?.let { if (month > it.monthValue) it.year - 1 else it.year } ?: return null
            yearText.length == 2 -> 2000 + yearText.toInt()
            else -> yearText.toInt()
        }
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }
}

/** Cálculos deterministas sobre los movimientos: no dependen de la IA. */
object InsightEngine {

    /** Monto máximo de una compra para considerarla "hormiga". */
    val HORMIGA_MAX = BigDecimal("200.00")
    private val REPEATED_MAX = BigDecimal("300.00")

    /** Marca los gastos hormiga y devuelve el resumen del periodo. */
    fun hormiga(movements: List<Movement>): Pair<List<Movement>, HormigaSummaryDto> {
        val purchases = movements.filter { it.tipo == MovementType.COMPRA && it.categoria != Categorizer.MENSUALIDADES }
        val repeated = purchases
            .filter { it.monto <= REPEATED_MAX }
            .groupBy { merchantKey(it.descripcion) }
            .filterValues { it.size >= 3 }
            .keys

        val marked = movements.map { m ->
            val isAnt = m.tipo == MovementType.COMPRA && m.categoria != Categorizer.MENSUALIDADES && (
                (m.monto <= HORMIGA_MAX && m.categoria in Categorizer.hormigaCategories) ||
                    (m.monto <= REPEATED_MAX && merchantKey(m.descripcion) in repeated)
                )
            if (isAnt) m.copy(esHormiga = true) else m
        }

        val ants = marked.filter { it.esHormiga }
        val total = ants.fold(ZERO) { acc, m -> acc + m.monto }
        val merchants = ants.groupBy { merchantKey(it.descripcion) }
            .map { (key, list) ->
                HormigaMerchantDto(
                    nombre = displayMerchant(list.first().descripcion, key),
                    veces = list.size,
                    total = list.fold(ZERO) { acc, m -> acc + m.monto }.plain(),
                )
            }
            .sortedByDescending { it.total.toBigDecimal() }
            .take(5)

        return marked to HormigaSummaryDto(
            total = total.plain(),
            compras = ants.size,
            proyeccionAnual = (total * BigDecimal(12)).plain(),
            comercios = merchants,
        )
    }

    data class AiFlag(val descripcion: String, val monto: BigDecimal?, val fecha: LocalDate?, val motivo: String)

    /** Cargos que conviene revisar, de los más graves a los menos. */
    fun suspicious(movements: List<Movement>, aiFlags: List<AiFlag> = emptyList()): Pair<List<Movement>, List<ChargeAlertDto>> {
        val purchases = movements.withIndex().filter {
            it.value.tipo == MovementType.COMPRA && it.value.categoria != Categorizer.MENSUALIDADES
        }
        val alerts = linkedMapOf<Int, ChargeAlertDto>()
        val flagged = mutableSetOf<Int>()

        fun flag(index: Int, motive: String, severity: AlertSeverity) {
            val m = movements[index]
            flagged += index
            val previous = alerts[index]
            if (previous == null || severity.ordinal < previous.severidad.ordinal) {
                alerts[index] = ChargeAlertDto(m.descripcion, m.monto.plain(), m.fecha?.toString(), motive, severity)
            }
        }

        // 1. Cargos repetidos: mismo comercio y monto en el mismo día o el siguiente.
        purchases.groupBy { merchantKey(it.value.descripcion) to it.value.monto }
            .values.filter { it.size >= 2 }
            .forEach { group ->
                val sorted = group.sortedBy { it.value.fecha }
                sorted.zipWithNext().forEach { (a, b) ->
                    val days = if (a.value.fecha != null && b.value.fecha != null) {
                        ChronoUnit.DAYS.between(a.value.fecha, b.value.fecha)
                    } else {
                        0
                    }
                    if (days <= 1) flag(b.index, "Cargo repetido: mismo comercio y monto casi al mismo tiempo.", AlertSeverity.ALTA)
                }
            }

        // 2. Compras en el extranjero u otra moneda.
        val foreign = Regex("\\bUSD\\b|\\bEUR\\b|DLLS?\\b|DOLAR|INTERNACIONAL|\\bINTL\\b|EXTRANJER|FOREIGN|\\bUS\\b|\\bUSA\\b")
        purchases.filter { foreign.containsMatchIn(normalizeText(it.value.descripcion)) }
            .forEach { flag(it.index, "Compra en el extranjero o en otra moneda: confirma que la reconoces.", AlertSeverity.MEDIA) }

        // 3. Montos muy por encima de lo habitual.
        val amounts = purchases.map { it.value.monto }.sorted()
        if (amounts.size >= 5) {
            val median = amounts[amounts.size / 2]
            val limit = maxOf(median * BigDecimal(5), BigDecimal("3000"))
            purchases.filter { it.value.monto > limit }
                .forEach { flag(it.index, "Monto mucho mayor que tus compras habituales.", AlertSeverity.MEDIA) }
        }

        // 4. Cargos de prueba: montos muy pequeños.
        purchases.filter { it.value.monto <= BigDecimal("10.00") }
            .forEach { flag(it.index, "Cargo muy pequeño: a veces se usa para probar tarjetas robadas.", AlertSeverity.BAJA) }

        // 5. Lo que señaló la IA (si coincide con un movimiento, se marca ese movimiento).
        val extra = mutableListOf<ChargeAlertDto>()
        aiFlags.forEach { ai ->
            val index = purchases.firstOrNull {
                (ai.monto == null || it.value.monto.compareTo(ai.monto) == 0) &&
                    merchantKey(it.value.descripcion) == merchantKey(ai.descripcion)
            }?.index
            val motive = ai.motivo.trim().take(160).ifBlank { "Cargo poco común: revisa que lo reconozcas." }
            if (index != null) {
                flag(index, motive, AlertSeverity.MEDIA)
            } else if (ai.monto != null) {
                extra += ChargeAlertDto(ai.descripcion.take(120), ai.monto.plain(), ai.fecha?.toString(), motive, AlertSeverity.MEDIA)
            }
        }

        val marked = movements.mapIndexed { i, m -> if (i in flagged) m.copy(sospechoso = true) else m }
        val list = (alerts.values + extra)
            .distinctBy { normalizeText(it.descripcion) + it.monto + it.fecha }
            .sortedBy { it.severidad.ordinal }
            .take(10)
        return marked to list
    }

    /** Total por categoría, incluidas las mensualidades a meses del periodo. */
    fun categories(movements: List<Movement>, installments: List<InstallmentPurchaseDto>): List<CategoryAmountDto> {
        val totals = linkedMapOf<String, BigDecimal>()
        movements.forEach { m ->
            when (m.tipo) {
                MovementType.COMPRA, MovementType.CARGO_FINANCIERO ->
                    totals.merge(m.categoria, m.monto, BigDecimal::add)
                MovementType.DEVOLUCION -> totals.merge(m.categoria, m.monto.negate(), BigDecimal::add)
                MovementType.PAGO -> Unit
            }
        }
        // Si el banco ya listó las mensualidades como movimientos, no se vuelven a sumar.
        if (movements.none { it.categoria == Categorizer.MENSUALIDADES }) {
            val monthly = installments.mapNotNull { parseMoney(it.mensualidad) }.fold(ZERO, BigDecimal::add)
            if (monthly.signum() > 0) totals.merge(Categorizer.MENSUALIDADES, monthly, BigDecimal::add)
        }
        return totals.filterValues { it.signum() > 0 }
            .map { (category, amount) -> CategoryAmountDto(category, amount.plain()) }
            .sortedByDescending { it.monto.toBigDecimal() }
    }

    /**
     * Desglose de la deuda: cada compra a meses y el saldo de compras normales (revolvente).
     * @return deuda total y su desglose.
     */
    fun debts(statementBalance: BigDecimal?, installments: List<InstallmentPurchaseDto>): Pair<BigDecimal, List<DebtItemDto>> {
        val items = installments.mapNotNull { plan ->
            val pending = parseMoney(plan.saldoPendiente)?.takeIf { it.signum() > 0 } ?: return@mapNotNull null
            DebtItemDto(plan.descripcion, pending.plain(), if (plan.conIntereses) "CON_INTERESES" else "MSI")
        }
        val plansTotal = items.fold(ZERO) { acc, d -> acc + d.monto.toBigDecimal() }
        // El saldo deudor total de los estados mexicanos ya incluye lo pendiente de los planes a meses.
        // Si el banco lo informa, esa es la deuda (puede ser menor que los planes si hay saldo a favor).
        val total = (statementBalance ?: plansTotal).money()
        val revolving = (total - plansTotal).money()
        val list = buildList {
            if (revolving.signum() > 0) add(DebtItemDto("Compras normales y saldo revolvente", revolving.plain(), "REVOLVENTE"))
            addAll(items.sortedByDescending { it.monto.toBigDecimal() })
            if (revolving.signum() < 0) add(DebtItemDto("Saldo a tu favor (se descuenta)", revolving.negate().plain(), "A_FAVOR"))
        }
        return total to list
    }

    /** Palabras que no identifican al comercio: sucursal, ciudad, moneda. */
    private val MERCHANT_NOISE = setOf(
        "SUC", "MEX", "CDMX", "GDL", "MTY", "COMPRA", "PAGO", "USD", "EUR", "MXN", "DLLS", "DLL", "INTL", "COM", "WWW",
    )

    /** Clave para agrupar el mismo comercio aunque cambie la sucursal o el folio. */
    internal fun merchantKey(description: String): String =
        normalizeText(description)
            .replace(Regex("[^A-Z ]"), " ")
            .split(" ")
            .filter { it.length >= 3 && it !in MERCHANT_NOISE }
            .take(2)
            .joinToString(" ")
            .ifBlank { normalizeText(description).take(12) }

    private fun displayMerchant(description: String, key: String): String =
        key.split(" ").joinToString(" ") { w -> w.lowercase().replaceFirstChar(Char::titlecase) }
            .ifBlank { description.take(30) }
}


/**
 * Lector local de la tabla de compras a meses, para formatos que el lector principal no reconoce:
 * - BBVA: "04-ago-2026 AMAZON A MESES $16,999.00 $15,582.42 $708.29 2 de 24 NA" (NA = sin intereses).
 * - Mercado Pago: "MERPAGO*TIENDA $ 1,094.00 0% 1 de 3 $ 364.68 $ 729.36".
 * Las compras ya liquidadas (saldo pendiente 0) no se incluyen.
 */
object LocalInstallmentParser {
    private const val M = "\\$?\\s*(\\d{1,3}(?:,\\d{3})*\\.\\d{2}|\\d+\\.\\d{2})"
    private const val DATE = "\\d{1,2}[-/ ][A-Za-z]{3,}[-/ ]\\d{2,4}|\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?"

    private val bbva = Regex("^(?:$DATE)\\s+(.+?)\\s+$M\\s+$M\\s+$M\\s+(\\d{1,3})\\s+de\\s+(\\d{1,3})\\s+(NA|N/A|[\\d.,]+\\s*%)\\s*$", RegexOption.IGNORE_CASE)
    private val mercadoPago = Regex("^(.+?)\\s+$M\\s+([\\d.,]+)\\s*%\\s+(\\d{1,3})\\s+de\\s+(\\d{1,3})\\s+$M\\s+$M\\s*$", RegexOption.IGNORE_CASE)

    fun parse(pages: List<String>): List<InstallmentPurchaseDto> {
        var interestFree: Boolean? = null
        val result = mutableListOf<InstallmentPurchaseDto>()
        pages.flatMap { it.lines() }.map { it.trim() }.forEach { line ->
            val text = normalizeText(line)
            when {
                "MESES SIN INTERESES" in text && text.startsWith("COMPRAS") -> interestFree = true
                "MESES CON INTERESES" in text && text.startsWith("COMPRAS") -> interestFree = false
                text.startsWith("CARGOS, ABONOS") || "COMPRAS REGULARES" in text -> interestFree = null
            }
            bbva.find(line)?.let { m ->
                val (desc, original, pending, monthly, current, total, rate) = m.destructured
                val ratePercent = rate.replace("%", "").trim().replace(",", ".").toBigDecimalOrNull()
                val free = interestFree ?: (ratePercent == null || ratePercent.signum() == 0)
                add(result, desc, original, pending, monthly, current, total, if (free) null else ratePercent, free)
                return@forEach
            }
            mercadoPago.find(line)?.let { m ->
                val (desc, original, rate, current, total, monthly, pending) = m.destructured
                val ratePercent = rate.replace(",", ".").toBigDecimalOrNull()
                val free = ratePercent == null || ratePercent.signum() == 0
                add(result, desc, original, pending, monthly, current, total, if (free) null else ratePercent, free)
            }
        }
        return result.distinctBy { normalizeText(it.descripcion) + it.saldoPendiente + it.pagoActual }
    }

    private fun add(
        into: MutableList<InstallmentPurchaseDto>,
        desc: String, original: String, pending: String, monthly: String,
        current: String, total: String, rate: BigDecimal?, free: Boolean,
    ) {
        val pendingAmount = parseMoney(pending)?.takeIf { it.signum() > 0 } ?: return
        into += InstallmentPurchaseDto(
            descripcion = LocalMovementParser.cleanDescription(desc).ifBlank { "Compra a meses" }.take(120),
            montoOriginal = parseMoney(original)?.plain(),
            mensualidad = parseMoney(monthly)?.plain(),
            pagoActual = current.toIntOrNull(),
            pagosTotales = total.toIntOrNull(),
            saldoPendiente = pendingAmount.plain(),
            conIntereses = !free,
            tasaAnual = rate?.stripTrailingZeros()?.toPlainString(),
        )
    }
}

/** Busca la fecha límite de pago aunque venga en el renglón siguiente o en una tabla de fechas. */
object DeadlineFinder {
    private val MONTHS = listOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC")
    private val textDate = Regex("(\\d{1,2})[\\s/-]+(ENE|FEB|MAR|ABR|MAY|JUN|JUL|AGO|SEPT?|OCT|NOV|DIC)[A-Z]*\\.?(?:[\\s/-]+(\\d{4}|\\d{2})\\b(?!\\s+[A-Z]))?")
    private val numericDate = Regex("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4}|\\d{2})\\b")
    private val label = Regex("FECHA L[IÍ]MITE (DE|PARA EL) PAGO")
    private val cutoffLabel = Regex("FECHA DE CORTE")

    /** Fecha de corte; sin año se toma la más reciente que no esté en el futuro respecto a [today]. */
    fun findCutoff(pages: List<String>, today: LocalDate): LocalDate? =
        findWith(cutoffLabel, pages, null, null, today)

    fun find(pages: List<String>, cutoff: LocalDate?, period: YearMonth?, today: LocalDate = LocalDate.now()): LocalDate? =
        findWith(label, pages, cutoff, period, today)

    private fun findWith(label: Regex, pages: List<String>, cutoff: LocalDate?, period: YearMonth?, today: LocalDate): LocalDate? {
        val base = cutoff ?: period?.atEndOfMonth()
        val lines = pages.flatMap { it.lines() }.map { normalizeText(it) }
        lines.forEachIndexed { i, line ->
            val match = label.find(line) ?: return@forEachIndexed
            // Definición del glosario ("Fecha límite de pago: es el día..."): no trae fecha.
            val rest = line.substring(match.range.last + 1)
            datesIn(rest, base, today).firstOrNull()?.let { return it }
            // Tabla: "Fecha de corte  Fecha límite de pago  Periodo" y debajo las fechas en el mismo orden.
            val position = Regex("FECHA").findAll(line.substring(0, match.range.first)).count()
            for (next in lines.subList(i + 1, minOf(i + 3, lines.size))) {
                val dates = datesIn(next, base, today)
                if (dates.isNotEmpty()) return dates.getOrNull(position) ?: dates.first()
            }
        }
        return null
    }

    private fun datesIn(text: String, base: LocalDate?, today: LocalDate): List<LocalDate> {
        val found = mutableListOf<Pair<Int, LocalDate>>()
        textDate.findAll(text).forEach { m ->
            val (d, mon, y) = m.destructured
            val month = MONTHS.indexOf(mon.take(3)) + 1
            build(d.toInt(), month, y, base, today)?.let { found += m.range.first to it }
        }
        numericDate.findAll(text).forEach { m ->
            val (d, mon, y) = m.destructured
            build(d.toInt(), mon.toInt(), y, base, today)?.let { found += m.range.first to it }
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    private fun build(day: Int, month: Int, yearText: String, base: LocalDate?, today: LocalDate): LocalDate? {
        if (month !in 1..12) return null
        val year = when {
            yearText.length == 4 -> yearText.toInt()
            yearText.length == 2 -> 2000 + yearText.toInt()
            // Sin año y con fecha de corte: el pago es después del corte (puede caer en el año siguiente).
            base != null -> if (month < base.monthValue) base.year + 1 else base.year
            // Sin referencia: el año más reciente que no quede más de 2 meses en el futuro.
            else -> if (YearMonth.of(today.year, month) > YearMonth.from(today).plusMonths(2)) today.year - 1 else today.year
        }
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }
}
