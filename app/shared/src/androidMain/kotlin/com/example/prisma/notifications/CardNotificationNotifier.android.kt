package com.example.prisma.notifications

import android.content.Context
import com.example.prisma.api.CardDto

actual object CardNotificationNotifier {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun scheduleCardReminders(card: CardDto) {
        val context = appContext ?: return
        CardNotificationScheduler.scheduleCardReminders(context, card)
    }

    actual fun cancelCardReminders(cardId: String) {
        val context = appContext ?: return
        CardNotificationScheduler.cancelCardReminders(context, cardId)
    }
}
