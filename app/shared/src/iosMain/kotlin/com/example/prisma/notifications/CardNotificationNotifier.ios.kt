package com.example.prisma.notifications

import com.example.prisma.api.CardDto

actual object CardNotificationNotifier {
    actual fun scheduleCardReminders(card: CardDto) {}
    actual fun cancelCardReminders(cardId: String) {}
}
