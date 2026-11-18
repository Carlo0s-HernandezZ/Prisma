package com.example.prisma.api

/**
 * Próximas fechas de un recordatorio. Vive en `core` para que el servidor (mensajes de Inicio)
 * y las apps (notificaciones programadas) calculen exactamente lo mismo.
 *
 * Trabaja con fechas como (año, mes, día) para no depender de una librería de fechas.
 */
object ReminderSchedule {

    data class Day(val year: Int, val month: Int, val day: Int) : Comparable<Day> {
        override fun compareTo(other: Day): Int =
            compareValuesBy(this, other, Day::year, Day::month, Day::day)

        override fun toString(): String =
            "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

        companion object {
            fun parse(iso: String): Day? {
                val p = iso.take(10).split('-').mapNotNull(String::toIntOrNull)
                return if (p.size == 3 && p[1] in 1..12 && p[2] in 1..31) Day(p[0], p[1], p[2]) else null
            }
        }
    }

    private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (isLeap(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    /** Días desde 1970-01-01 (algoritmo civil de Howard Hinnant). */
    fun epochDay(d: Day): Long {
        val y = if (d.month <= 2) d.year - 1 else d.year
        val era = y.floorDiv(400)
        val yoe = y - era * 400
        val mp = (d.month + 9) % 12
        val doy = (153 * mp + 2) / 5 + d.day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }

    fun fromEpochDay(days: Long): Day {
        val z = days + 719_468L
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097L
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
        return Day(year, month, day)
    }

    /** 1 = lunes … 7 = domingo. */
    fun dayOfWeek(d: Day): Int = ((epochDay(d) + 3).mod(7L) + 1).toInt()

    /** ¿El recordatorio toca en [d]? Un día 31 en un mes de 30 cae en el último día del mes. */
    fun occursOn(r: ReminderDto, d: Day): Boolean {
        fun monthly(day: Int?) = day != null && minOf(day, daysInMonth(d.year, d.month)) == d.day
        return when (r.frecuencia) {
            ReminderFrequency.MENSUAL -> monthly(r.dia)
            ReminderFrequency.QUINCENAL -> monthly(r.dia) || monthly(r.dia2)
            ReminderFrequency.SEMANAL -> r.dia == dayOfWeek(d)
            ReminderFrequency.CATORCENAL -> {
                val start = r.fechaInicio?.let(Day::parse) ?: return false
                val diff = epochDay(d) - epochDay(start)
                diff >= 0 && diff % 14 == 0L
            }
        }
    }

    /** Las próximas [count] fechas (desde [from], incluido) en que toca el recordatorio. */
    fun nextDates(r: ReminderDto, from: Day, count: Int = 6, horizonDays: Int = 400): List<Day> {
        if (!r.activo) return emptyList()
        val start = epochDay(from)
        return (0..horizonDays).asSequence()
            .map { fromEpochDay(start + it) }
            .filter { occursOn(r, it) }
            .take(count)
            .toList()
    }

    /** Valida los datos del recordatorio; devuelve el mensaje de error o null si está bien. */
    fun validate(r: ReminderDto): String? {
        val hourOk = Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(r.hora)
        return when {
            r.titulo.isBlank() || r.titulo.length > 80 -> "Escribe un nombre de hasta 80 caracteres."
            !hourOk -> "La hora debe tener formato HH:mm."
            r.tipo == ReminderKind.ESTADO_CUENTA && r.tarjetaId.isNullOrBlank() -> "Elige la tarjeta del estado de cuenta."
            r.frecuencia == ReminderFrequency.MENSUAL && r.dia !in 1..31 -> "Elige un día del mes (1 a 31)."
            r.frecuencia == ReminderFrequency.QUINCENAL && (r.dia !in 1..31 || r.dia2 !in 1..31 || r.dia == r.dia2) ->
                "Elige dos días distintos del mes."
            r.frecuencia == ReminderFrequency.SEMANAL && r.dia !in 1..7 -> "Elige un día de la semana."
            r.frecuencia == ReminderFrequency.CATORCENAL && r.fechaInicio?.let(Day::parse) == null ->
                "Elige la fecha de tu próximo pago."
            else -> null
        }
    }

    /** Descripción corta: "Cada mes, el día 5 a las 09:00". */
    fun describe(r: ReminderDto): String {
        val weekdays = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
        val whenText = when (r.frecuencia) {
            ReminderFrequency.MENSUAL -> "Cada mes, el día ${r.dia}"
            ReminderFrequency.QUINCENAL -> "Cada quincena, los días ${r.dia} y ${r.dia2}"
            ReminderFrequency.SEMANAL -> "Cada ${weekdays.getOrElse((r.dia ?: 1) - 1) { "semana" }}"
            ReminderFrequency.CATORCENAL -> "Cada 14 días desde ${r.fechaInicio}"
        }
        return "$whenText a las ${r.hora}"
    }
}
