package com.example.prisma

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform