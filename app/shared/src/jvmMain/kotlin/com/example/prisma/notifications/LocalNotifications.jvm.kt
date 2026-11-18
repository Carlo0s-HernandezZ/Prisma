package com.example.prisma.notifications

import com.example.prisma.api.ReminderDto

/** Sin notificaciones del sistema en esta plataforma: los recordatorios se muestran en Inicio. */
actual object LocalNotifications {
    actual fun requestPermission() = Unit
    actual fun notifyNow(id: Int, title: String, body: String) = Unit
    actual fun syncReminders(reminders: List<ReminderDto>, cardNames: Map<String, String>) = Unit
}
