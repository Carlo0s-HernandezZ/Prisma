package com.example.prisma.network

import com.example.prisma.SERVER_CERT_PEM
import com.example.prisma.SERVER_CERT_PIN
import com.example.prisma.SERVER_HOST
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Cliente HTTP de Android con comunicaciones cifradas y anclaje de certificado (OWASP M5 / MASVS-NETWORK):
 *
 * 1. **Validación del certificado**: el TrustManager solo contiene el certificado de Prisma; cualquier
 *    otro (incluso uno emitido por una CA instalada en el teléfono por un atacante) se rechaza.
 *    OkHttp además verifica que el nombre/IP del servidor esté en el certificado (hostname verification).
 * 2. **Certificate Pinning**: CertificatePinner compara el SHA-256 de la llave pública del servidor
 *    con [SERVER_CERT_PIN]; si no coincide la conexión se corta (`SSLPeerUnverifiedException`).
 * 3. **Solo TLS 1.3 / 1.2** con suites modernas; HTTP sin cifrar está prohibido también en el
 *    manifiesto (`network_security_config.xml`, cleartextTrafficPermitted=false).
 */
internal actual fun createPlatformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp) {
        config()
        engine {
            config {
                val trustManager = prismaTrustManager()
                val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
                sslSocketFactory(sslContext.socketFactory, trustManager)
                certificatePinner(
                    CertificatePinner.Builder()
                        .add(SERVER_HOST, SERVER_CERT_PIN)
                        .add("10.0.2.2", SERVER_CERT_PIN) // emulador de Android → computadora
                        .add("localhost", SERVER_CERT_PIN)
                        .build(),
                )
                connectionSpecs(
                    listOf(
                        ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                            .build(),
                    ),
                )
            }
        }
    }

/** TrustManager cuya única raíz de confianza es el certificado del servidor de Prisma. */
private fun prismaTrustManager(): X509TrustManager {
    val certificate = CertificateFactory.getInstance("X.509")
        .generateCertificate(SERVER_CERT_PEM.byteInputStream())
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null, null)
        setCertificateEntry("prisma", certificate)
    }
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keyStore) }
    return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
}
