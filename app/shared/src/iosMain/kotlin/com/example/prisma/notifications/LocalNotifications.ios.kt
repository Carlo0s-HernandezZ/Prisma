package com.example.prisma.notifications

import com.example.prisma.api.ReminderDto
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/** Muestra también los avisos cuando la app está abierta (iOS los oculta por defecto). */
private class ForegroundPresenter : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound)
    }
}

actual object LocalNotifications {
    private const val REMINDER_PREFIX = "prisma-recordatorio-"
    /** Avisos programados por recordatorio (iOS permite hasta 64 pendientes en total). */
    private const val PER_REMINDER = 6

    // El centro de notificaciones guarda el delegado como referencia débil: aquí se mantiene vivo.
    private val presenter = ForegroundPresenter()
    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    actual fun requestPermission() {
        center.delegate = presenter
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { _, _ -> }
    }

    actual fun notifyNow(id: Int, title: String, body: String) {
        center.delegate = presenter
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound)
        }
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier("prisma-aviso-$id", content, null), null)
    }

    actual fun syncReminders(reminders: List<ReminderDto>, cardNames: Map<String, String>) {
        center.getPendingNotificationRequestsWithCompletionHandler { pending ->
            val old = pending.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier }.filter { it.startsWith(REMINDER_PREFIX) }
            center.removePendingNotificationRequestsWithIdentifiers(old)

            val now = now()
            reminders.filter { it.activo }.forEach { r ->
                ReminderPlanner.upcoming(r, now, PER_REMINDER).forEachIndexed { index, at ->
                    val content = UNMutableNotificationContent().apply {
                        setTitle(ReminderPlanner.title(r))
                        setBody(ReminderPlanner.body(r, cardNames))
                        setSound(UNNotificationSound.defaultSound)
                    }
                    val components = NSDateComponents().apply {
                        year = at.year.toLong()
                        month = at.month.toLong()
                        day = at.day.toLong()
                        hour = at.hour.toLong()
                        minute = at.minute.toLong()
                    }
                    val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
                    center.addNotificationRequest(
                        UNNotificationRequest.requestWithIdentifier("$REMINDER_PREFIX${r.id}-$index", content, trigger),
                        null,
                    )
                }
            }
        }
    }

    private fun now(): ReminderTime {
        val units = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or NSCalendarUnitHour or NSCalendarUnitMinute
        val c = NSCalendar.currentCalendar.components(units, fromDate = NSDate())
        return ReminderTime(c.year.toInt(), c.month.toInt(), c.day.toInt(), c.hour.toInt(), c.minute.toInt())
    }
}
