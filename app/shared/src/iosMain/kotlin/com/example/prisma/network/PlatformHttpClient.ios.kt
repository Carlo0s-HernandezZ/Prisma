package com.example.prisma.network

import com.example.prisma.SERVER_CERT_PEM
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust
import platform.Security.SecCertificateCopyData
import platform.Security.SecTrustGetCertificateAtIndex
import platform.Security.SecTrustRef
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Cliente HTTP de iOS (URLSession) con **Certificate Pinning**: el certificado que presenta el
 * servidor debe ser exactamente el de Prisma (comparación byte a byte del DER). Si no coincide
 * —por ejemplo, un proxy de intercepción MitM en una red pública— la conexión se cancela.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun createPlatformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Darwin) {
        config()
        engine {
            handleChallenge { _, _, challenge, completionHandler ->
                val space = challenge.protectionSpace
                if (space.authenticationMethod != NSURLAuthenticationMethodServerTrust) {
                    completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
                    return@handleChallenge
                }
                val trust = space.serverTrust
                if (trust != null && isPrismaCertificate(trust)) {
                    completionHandler(NSURLSessionAuthChallengeUseCredential, NSURLCredential.credentialForTrust(trust))
                } else {
                    completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                }
            }
        }
    }

@OptIn(ExperimentalEncodingApi::class)
private val pinnedCertificateDer: ByteArray by lazy {
    val body = SERVER_CERT_PEM.lines().filterNot { it.startsWith("-----") }.joinToString("")
    Base64.decode(body)
}

@OptIn(ExperimentalForeignApi::class)
private fun isPrismaCertificate(trust: SecTrustRef): Boolean {
    @Suppress("DEPRECATION")
    val leaf = SecTrustGetCertificateAtIndex(trust, 0) ?: return false
    val data = SecCertificateCopyData(leaf) ?: return false
    return try {
        val length = CFDataGetLength(data).toInt()
        val pointer = CFDataGetBytePtr(data) ?: return false
        val presented = ByteArray(length) { pointer[it].toByte() }
        presented.contentEquals(pinnedCertificateDer)
    } finally {
        CFRelease(data)
    }
}
