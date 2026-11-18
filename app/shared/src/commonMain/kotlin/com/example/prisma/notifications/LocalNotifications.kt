package com.example.prisma.notifications

import com.example.prisma.api.ReminderDto
import com.example.prisma.api.ReminderKind
import com.example.prisma.api.ReminderSchedule

/**
 * Notificaciones locales del teléfono:
 * - avisos inmediatos (p. ej. irregularidades en la nómina);
 * - recordatorios programados por el usuario para subir su nómina o un estado de cuenta.
 *
 * Android e iOS las muestran; en web y escritorio no hacen nada (los recordatorios se ven en Inicio).
 */
expect object LocalNotifications {
    /** Pide permiso para mostrar notificaciones (iOS; en Android se pide al abrir la app). */
    fun requestPermission()

    fun notifyNow(id: Int, title: String, body: String)

    /** Cancela los recordatorios programados antes y programa los activos de [reminders]. */
    fun syncReminders(reminders: List<ReminderDto>, cardNames: Map<String, String>)
}

/** Fecha y hora local de un aviso. */
data class ReminderTime(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int) : Comparable<ReminderTime> {
    override fun compareTo(other: ReminderTime): Int =
        compareValuesBy(this, other, ReminderTime::year, ReminderTime::month, ReminderTime::day, ReminderTime::hour, ReminderTime::minute)
}

/** Cálculos compartidos por Android e iOS para programar los recordatorios. */
object ReminderPlanner {

    /** Próximos [count] avisos posteriores a [now]. */
    fun upcoming(r: ReminderDto, now: ReminderTime, count: Int): List<ReminderTime> {
        val (hour, minute) = r.hora.split(':').mapNotNull(String::toIntOrNull).let { (it.getOrNull(0) ?: 9) to (it.getOrNull(1) ?: 0) }
        return ReminderSchedule.nextDates(r, ReminderSchedule.Day(now.year, now.month, now.day), count + 1)
            .map { ReminderTime(it.year, it.month, it.day, hour, minute) }
            .filter { it > now }
            .take(count)
    }

    fun title(r: ReminderDto): String = r.titulo

    fun body(r: ReminderDto, cardNames: Map<String, String>): String =
        if (r.tipo == ReminderKind.NOMINA) {
            "Es momento de subir tu nómina a Prisma para revisarla."
        } else {
            val card = r.tarjetaId?.let(cardNames::get) ?: "tu tarjeta"
            "Es momento de subir el estado de cuenta de $card en Análisis."
        }

    /** Identificador numérico estable para cada aviso de un recordatorio. */
    fun requestCode(r: ReminderDto, index: Int = 0): Int = 70_000 + (r.id.hashCode() and 0x3FFF) * 8 + index
}
