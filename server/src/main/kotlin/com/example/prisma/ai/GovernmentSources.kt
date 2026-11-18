package com.example.prisma.ai

import com.example.prisma.api.AdviceDto
import java.net.URI

/**
 * Fuentes oficiales del Gobierno de México de las que pueden salir los consejos de Prisma.
 *
 * La IA solo elige el identificador de la fuente; la URL siempre la pone el servidor desde esta lista,
 * así un consejo nunca enlaza a un sitio inventado o no oficial.
 */
enum class GovernmentSource(val nombre: String, val url: String) {
    CONDUSEF("CONDUSEF", "https://www.condusef.gob.mx"),
    CONDUSEF_GOB("CONDUSEF · gob.mx", "https://www.gob.mx/condusef"),
    BANXICO("Banco de México", "https://www.banxico.org.mx"),
    PROFECO("PROFECO", "https://www.gob.mx/profeco"),
    BURO_ENTIDADES("Buró de Entidades Financieras", "https://www.buro.gob.mx"),
    CNBV("CNBV", "https://www.gob.mx/cnbv"),
    SAT("SAT", "https://www.sat.gob.mx"),
    IMSS("IMSS", "https://www.imss.gob.mx"),
    PROFEDET("PROFEDET", "https://www.gob.mx/profedet"),
    ;

    companion object {
        fun fromId(id: String?): GovernmentSource? =
            entries.firstOrNull { it.name.equals(id?.trim(), ignoreCase = true) }

        /** true solo para dominios del Gobierno de México (gob.mx) y Banco de México. */
        fun isOfficialUrl(url: String): Boolean {
            val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
            if (!url.startsWith("https://")) return false
            return host == "gob.mx" || host.endsWith(".gob.mx") ||
                host == "banxico.org.mx" || host == "www.banxico.org.mx"
        }
    }
}

/** Filtro de consejos: Prisma no recomienda dónde invertir, solo hábitos de salud financiera. */
object AdvicePolicy {

    private val investment = Regex(
        "(invier|invert|inversi|acciones\\b|bolsa de valores|cripto|bitcoin|fondos? de inversi|" +
            "cetes|bonos\\b|forex|trading|rendimiento|afore voluntari|casa de bolsa|portafolio)",
        RegexOption.IGNORE_CASE,
    )

    fun isAllowed(titulo: String, texto: String): Boolean =
        titulo.isNotBlank() && texto.isNotBlank() &&
            !investment.containsMatchIn(titulo) && !investment.containsMatchIn(texto)

    /** Convierte la sugerencia de la IA en consejo solo si cumple la política; si no, null. */
    fun accept(titulo: String?, texto: String?, fuenteId: String?): AdviceDto? {
        val source = GovernmentSource.fromId(fuenteId) ?: return null
        val t = titulo?.trim()?.take(90).orEmpty()
        val body = texto?.trim()?.take(400).orEmpty()
        if (!isAllowed(t, body) || !GovernmentSource.isOfficialUrl(source.url)) return null
        return AdviceDto(t, body, source.nombre, source.url)
    }
}

/** Situaciones detectadas en los estados que hacen relevante un consejo. */
enum class AdviceTrigger { DEUDA_REVOLVENTE, CON_INTERESES, MSI, HORMIGA, ALERTAS, SOLO_MINIMO, GENERAL }

/**
 * Consejos escritos por Prisma a partir de los materiales de educación financiera de cada institución.
 * Se usan cuando la IA no está disponible y para completar los de la IA.
 */
object LocalAdvice {

    data class Tip(
        val id: String,
        val titulo: String,
        val texto: String,
        val fuente: GovernmentSource,
        val trigger: AdviceTrigger,
    ) {
        fun toDto() = AdviceDto(titulo, texto, fuente.nombre, fuente.url)
    }

    val tips = listOf(
        Tip(
            "paga_mas_minimo", "Paga más del mínimo",
            "Si solo cubres el pago mínimo, casi todo se va a intereses y la deuda tarda años en bajar. " +
                "Siempre que puedas, cubre el pago para no generar intereses.",
            GovernmentSource.CONDUSEF, AdviceTrigger.DEUDA_REVOLVENTE,
        ),
        Tip(
            "solo_minimo", "El pago mínimo sale caro",
            "Pagar solo el mínimo mes con mes hace que pagues mucho más por lo que compraste. " +
                "Abona un poco más cada mes, aunque sea una cantidad pequeña.",
            GovernmentSource.CONDUSEF_GOB, AdviceTrigger.SOLO_MINIMO,
        ),
        Tip(
            "compara_cat", "Compara el CAT antes de diferir",
            "Antes de comprar a meses con intereses, revisa el CAT (Costo Anual Total): incluye intereses y comisiones " +
                "y te dice cuánto te cuesta realmente el crédito.",
            GovernmentSource.BANXICO, AdviceTrigger.CON_INTERESES,
        ),
        Tip(
            "suma_mensualidades", "Suma tus mensualidades",
            "Los meses sin intereses también comprometen tu ingreso de los próximos meses. " +
                "Antes de otra compra a meses, suma las mensualidades que ya tienes.",
            GovernmentSource.CONDUSEF, AdviceTrigger.MSI,
        ),
        Tip(
            "gastos_hormiga", "Ponle nombre a tus gastos hormiga",
            "Un café o un antojo diario parecen poco, pero al mes suman. Anótalos una semana " +
                "y elige uno o dos que puedas reducir.",
            GovernmentSource.CONDUSEF_GOB, AdviceTrigger.HORMIGA,
        ),
        Tip(
            "compara_precios", "Compara precios antes de comprar",
            "Revisa precios en varios lugares antes de comprar; PROFECO publica comparativos " +
                "que te ayudan a gastar menos en lo mismo.",
            GovernmentSource.PROFECO, AdviceTrigger.HORMIGA,
        ),
        Tip(
            "cargos_no_reconocidos", "Reporta los cargos que no reconozcas",
            "Llama a tu banco en cuanto veas un cargo que no hiciste y pide la aclaración por escrito. " +
                "Si no te resuelven, CONDUSEF puede ayudarte.",
            GovernmentSource.CONDUSEF, AdviceTrigger.ALERTAS,
        ),
        Tip(
            "fechas_tarjeta", "Ubica tu fecha de corte y de pago",
            "Las compras después del corte se pagan hasta el siguiente mes. Pagar a tiempo, antes de la fecha límite, " +
                "evita intereses moratorios y comisiones.",
            GovernmentSource.CONDUSEF, AdviceTrigger.GENERAL,
        ),
        Tip(
            "presupuesto", "Haz un presupuesto mensual",
            "Anota cuánto ganas y en qué gastas. Separa primero lo indispensable y los pagos de tus tarjetas; " +
                "lo que sobre puedes destinarlo a tus metas.",
            GovernmentSource.CONDUSEF_GOB, AdviceTrigger.GENERAL,
        ),
        Tip(
            "ahorro_imprevistos", "Aparta un ahorro para imprevistos",
            "Guardar poco a poco para emergencias evita que uses la tarjeta cuando surge algo inesperado.",
            GovernmentSource.CONDUSEF_GOB, AdviceTrigger.GENERAL,
        ),
        Tip(
            "no_uses_todo_el_limite", "No uses todo tu límite",
            "Mantén tu saldo lejos del límite de crédito: usarlo casi completo complica tus pagos " +
                "y afecta tu historial crediticio.",
            GovernmentSource.CONDUSEF, AdviceTrigger.DEUDA_REVOLVENTE,
        ),
        Tip(
            "revisa_institucion", "Conoce a tu banco",
            "Antes de contratar una tarjeta, consulta en el Buró de Entidades Financieras las quejas, " +
                "comisiones y sanciones de cada institución.",
            GovernmentSource.BURO_ENTIDADES, AdviceTrigger.GENERAL,
        ),
        Tip(
            "programa_pagos", "Programa tus pagos",
            "Pon un recordatorio o domicilia el pago de tu tarjeta para no olvidarlo y evitar recargos.",
            GovernmentSource.CONDUSEF, AdviceTrigger.GENERAL,
        ),
    )

    fun byId(id: String): Tip? = tips.firstOrNull { it.id == id }

    /** Consejos para las situaciones detectadas, primero los más específicos. */
    fun forTriggers(triggers: Set<AdviceTrigger>, limit: Int = 4): List<AdviceDto> =
        (tips.filter { it.trigger in triggers } + tips.filter { it.trigger == AdviceTrigger.GENERAL })
            .distinctBy { it.id }
            .take(limit)
            .map { it.toDto() }

    /** Consejo del día cuando la IA no está disponible: rota según el día del año. */
    fun ofTheDay(dayOfYear: Int, triggers: Set<AdviceTrigger>): Tip {
        val pool = tips.filter { it.trigger in triggers || it.trigger == AdviceTrigger.GENERAL }
        return pool[dayOfYear.mod(pool.size)]
    }
}
