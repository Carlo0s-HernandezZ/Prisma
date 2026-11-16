package com.example.prisma.notifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.prisma.api.CardDto
import java.util.Calendar

object CardNotificationScheduler {

    fun scheduleCardReminders(context: Context, card: CardDto) {
        cancelCardReminders(context, card.id)

        if (card.diaCorte != null && !card.horaNotificacionCorte.isNullOrBlank()) {
            scheduleAlarm(
                context = context,
                cardId = card.id,
                bank = card.banco,
                alias = card.alias,
                day = card.diaCorte!!,
                timeStr = card.horaNotificacionCorte!!,
                eventType = "Corte",
                requestCode = (card.id.hashCode() * 31) + 1,
            )
        }

        if (card.diaPago != null && !card.horaNotificacionPago.isNullOrBlank()) {
            scheduleAlarm(
                context = context,
                cardId = card.id,
                bank = card.banco,
                alias = card.alias,
                day = card.diaPago!!,
                timeStr = card.horaNotificacionPago!!,
                eventType = "Pago",
                requestCode = (card.id.hashCode() * 31) + 2,
            )
        }
    }

    fun cancelCardReminders(context: Context, cardId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val receiverClass = getReceiverClass(context)

        val intentCorte = Intent(context, receiverClass)
        val pendingCorte = PendingIntent.getBroadcast(
            context,
            (cardId.hashCode() * 31) + 1,
            intentCorte,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingCorte)

        val intentPago = Intent(context, receiverClass)
        val pendingPago = PendingIntent.getBroadcast(
            context,
            (cardId.hashCode() * 31) + 2,
            intentPago,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingPago)
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarm(
        context: Context,
        cardId: String,
        bank: String,
        alias: String,
        day: Int,
        timeStr: String,
        eventType: String,
        requestCode: Int,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            val maxDayInMonth = getActualMaximum(Calendar.DAY_OF_MONTH)
            val validDay = day.coerceAtMost(maxDayInMonth)
            set(Calendar.DAY_OF_MONTH, validDay)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.MONTH, 1)
                val newMaxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                set(Calendar.DAY_OF_MONTH, day.coerceAtMost(newMaxDay))
            }
        }

        val receiverClass = getReceiverClass(context)
        val intent = Intent(context, receiverClass).apply {
            putExtra("CARD_ID", cardId)
            putExtra("CARD_BANK", bank)
            putExtra("CARD_ALIAS", alias)
            putExtra("EVENT_TYPE", eventType)
            putExtra("REQUEST_CODE", requestCode)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        } catch (_: Exception) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getReceiverClass(context: Context): Class<*> {
        return try {
            Class.forName("com.example.prisma.notifications.CardReminderReceiver")
        } catch (_: Exception) {
            com.example.prisma.notifications.CardNotificationScheduler::class.java
        }
    }
}
