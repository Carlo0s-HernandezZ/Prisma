package com.example.prisma

interface Platform {
    val name: String

    /** true en Android/iOS: ahí la app de autenticación está en el mismo dispositivo. */
    val isMobile: Boolean get() = false
}

expect fun getPlatform(): Platform