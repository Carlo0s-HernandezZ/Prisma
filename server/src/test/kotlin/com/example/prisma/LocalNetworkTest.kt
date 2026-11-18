package com.example.prisma

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** CORS por defecto: la app web local funciona en cualquier red sin reiniciar el servidor. */
class LocalNetworkTest {
    private val ports = setOf(3000, 3001, 8080)

    @Test
    fun allowsLocalOriginsOnAnyNetwork() {
        listOf(
            "http://localhost:3001", "http://127.0.0.1:3000", "http://MacBook-Air-de-Juan.local:3001",
            "http://192.168.100.20:3001", "http://192.168.1.8:3001", "http://10.0.0.5:3001", "http://172.20.1.2:3000",
        ).forEach { assertTrue(isLocalWebOrigin(it, ports), it) }
    }

    @Test
    fun rejectsPublicOrOtherPorts() {
        listOf(
            "https://evil.com:3001", "http://8.8.8.8:3001", "http://192.168.1.8:4000", "http://172.40.1.2:3001",
            "http://localhost.evil.com:3001", "ftp://localhost:3001", "http://192.168.1:3001",
        ).forEach { assertFalse(isLocalWebOrigin(it, ports), it) }
    }
}
