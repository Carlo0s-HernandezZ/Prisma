package com.example.prisma.network

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import org.slf4j.LoggerFactory

/**
 * Anuncia el servidor en la red local por mDNS/DNS-SD como servicio `_prisma._tcp`.
 *
 * Así los teléfonos encuentran el servidor en **cualquier red** sin conocer su IP:
 * - iPhone y computadoras resuelven el nombre `<equipo>.local` de forma nativa (Bonjour).
 * - Android, si su sistema no resuelve `.local`, busca este servicio con NsdManager (PrismaDns).
 *
 * Se anuncia solo en las IPv4 privadas (el servidor escucha en 0.0.0.0) y cada 15 s se revisan las
 * interfaces: al cambiar de Wi-Fi con el servidor encendido, el anuncio se actualiza solo.
 * Si la red no permite multicast, el servidor sigue funcionando; solo no se anuncia.
 */
object LocalNetworkAdvertiser {
    const val SERVICE_TYPE = "_prisma._tcp.local."

    private val log = LoggerFactory.getLogger(LocalNetworkAdvertiser::class.java)
    private val active = mutableMapOf<InetAddress, JmDNS>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "mdns").apply { isDaemon = true } }

    /** Nombre del equipo sin dominio ("MacBook-Air-de-Juan"); el servicio se llama "Prisma (<equipo>)". */
    fun machineName(): String =
        runCatching {
            ProcessBuilder("scutil", "--get", "LocalHostName").redirectErrorStream(true).start().let { p ->
                p.waitFor(2, TimeUnit.SECONDS)
                p.inputStream.bufferedReader().readText().trim().takeIf { p.exitValue() == 0 && it.isNotBlank() }
            }
        }.getOrNull()
            ?: runCatching { InetAddress.getLocalHost().hostName.substringBefore('.') }.getOrNull()
            ?: "prisma"

    fun start(httpsPort: Int) {
        val machine = machineName()
        scheduler.scheduleWithFixedDelay({ refresh(machine, httpsPort) }, 0, 15, TimeUnit.SECONDS)
        Runtime.getRuntime().addShutdownHook(Thread { synchronized(active) { active.values.forEach(::close); active.clear() } })
    }

    /** IPv4 privadas activas de esta computadora (Wi-Fi, cable). */
    private fun lanAddresses(): Set<InetAddress> =
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual && it.supportsMulticast() }
                .flatMap { it.inetAddresses.toList() }
                .filter { it is Inet4Address && it.isSiteLocalAddress }
                .toSet()
        }.getOrDefault(emptySet())

    private fun refresh(machine: String, port: Int) = synchronized(active) {
        val current = lanAddresses()
        (active.keys - current).forEach { gone -> active.remove(gone)?.let(::close) }
        (current - active.keys).forEach { address ->
            runCatching {
                // Nombre propio ("prisma-<equipo>") para no chocar con el que ya anuncia el sistema.
                val mdns = JmDNS.create(address, "prisma-$machine")
                mdns.registerService(ServiceInfo.create(SERVICE_TYPE, "Prisma ($machine)", port, "host=$machine.local"))
                active[address] = mdns
                log.info("Anunciado en la red local como \"Prisma ($machine)\" en ${address.hostAddress} · https://$machine.local:$port")
            }.onFailure { log.warn("No se pudo anunciar el servidor por mDNS en ${address.hostAddress}: ${it.message}") }
        }
    }

    private fun close(mdns: JmDNS) {
        runCatching {
            mdns.unregisterAllServices()
            mdns.close()
        }
    }
}
