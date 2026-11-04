package com.example.prisma

interface Platform {
    val name: String

    /** true en Android/iOS: ahí la app de autenticación está en el mismo dispositivo. */
    val isMobile: Boolean get() = false

    /** true en el navegador (JS / Wasm): habilita el diseño de escritorio en pantallas anchas. */
    val isWeb: Boolean get() = false
}

expect fun getPlatform(): Platform