package com.example.prisma.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.example.prisma.SERVER_LAN_HINT
import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Llamar una vez al iniciar la app (MainActivity) para que Android pueda encontrar el servidor
 * en la red local por su nombre `.local`.
 */
object PrismaNetwork {
    fun init(context: Context) {
        PrismaDns.context = context.applicationContext
    }
}

/**
 * Resuelve el nombre del servidor ("MacBook-Air-de-Juan.local") en cualquier red, sin IP fija:
 *
 * 1. DNS del sistema (Android reciente y el emulador resuelven `.local`); se usan solo las IPv4.
 * 2. Emulador (sin multicast): la IP de la computadora al compilar y 10.0.2.2.
 * 3. Teléfono físico: busca en la red el servicio `_prisma._tcp` que anuncia el servidor (NsdManager);
 *    si no lo encuentra, prueba la IP de la computadora al compilar.
 *
 * La seguridad no cambia: el certificado del servidor sigue anclado (pin SHA-256) y debe incluir el nombre.
 */
internal object PrismaDns : Dns {
    @Volatile
    var context: Context? = null

    private const val SERVICE_TYPE = "_prisma._tcp."
    private const val DISCOVERY_TIMEOUT_SECONDS = 5L
    private const val CACHE_MILLIS = 60_000L

    private data class Cached(val host: String, val addresses: List<InetAddress>, val at: Long)

    @Volatile
    private var cached: Cached? = null

    override fun lookup(hostname: String): List<InetAddress> {
        val local = hostname.endsWith(".local", ignoreCase = true)
        val system = runCatching { Dns.SYSTEM.lookup(hostname) }
        // El servidor escucha en IPv4 (0.0.0.0): una respuesta solo con IPv6 no sirve para conectarse.
        system.getOrNull()?.filterIsInstance<Inet4Address>()?.takeIf { it.isNotEmpty() }?.let { return it }
        if (!local) {
            system.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
            throw system.exceptionOrNull() ?: UnknownHostException(hostname)
        }
        // Emulador (sin multicast): la IP de la computadora al compilar y 10.0.2.2; OkHttp prueba ambas.
        if (isEmulator()) return emulatorFallback()

        cached?.takeIf { it.host == hostname && System.currentTimeMillis() - it.at < CACHE_MILLIS }?.let { return it.addresses }
        val found = discover(hostname.substringBefore('.'))
            ?: lanHint()?.firstOrNull()
            ?: throw UnknownHostException(
                "No se encontró el servidor de Prisma ($hostname) en esta red. Conéctate a la misma Wi-Fi que la computadora.",
            )
        cached = Cached(hostname, listOf(found), System.currentTimeMillis())
        return listOf(found)
    }

    private fun lanHint(): List<InetAddress>? =
        runCatching { listOf(InetAddress.getByName(SERVER_LAN_HINT)) }.getOrNull()
            ?.filter { it is Inet4Address && it.isSiteLocalAddress }?.takeIf { it.isNotEmpty() }

    private fun emulatorFallback(): List<InetAddress> =
        lanHint().orEmpty() + InetAddress.getByName("10.0.2.2")

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") ||
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" || Build.PRODUCT.contains("sdk")

    /** Busca el servicio `_prisma._tcp`; prefiere el que anuncia la computadora indicada ("Prisma (<equipo>)"). */
    @Suppress("DEPRECATION") // resolveService sigue disponible y es compatible con Android 7+.
    private fun discover(machine: String): InetAddress? {
        val nsd = context?.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        val result = AtomicReference<InetAddress?>(null)
        val done = CountDownLatch(1)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                val name = service.serviceName.orEmpty()
                if (!name.startsWith("Prisma")) return
                // Si hay varias computadoras con Prisma en la red, se elige la que coincide con el nombre.
                if (!name.contains(machine, ignoreCase = true) && result.get() != null) return
                nsd.resolveService(
                    service,
                    object : NsdManager.ResolveListener {
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val address = info.host ?: return
                            result.set(address)
                            if (info.serviceName.contains(machine, ignoreCase = true)) done.countDown()
                        }

                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                    },
                )
            }

            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = done.countDown()
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        return try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            done.await(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            result.get()
        } catch (_: Exception) {
            result.get()
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }
}
