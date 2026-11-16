package com.example.prisma.notifications

import com.example.prisma.api.CardDto

expect object CardNotificationNotifier {
    fun scheduleCardReminders(card: CardDto)
    fun cancelCardReminders(cardId: String)
}
