import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Properties
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// ---------- IP del servidor (automática) ----------
//
// "localhost" en un celular apunta al propio celular, no a esta computadora. Por eso, en cada
// compilación se detecta la IP de red de la computadora y se escribe en ServerConfig.kt (generado
// en build/, no se sube a git). Al cambiar de red basta con volver a compilar/instalar la app.
//
// Para forzar otra dirección (p. ej. si el servidor corre en otra computadora), agrega a
// local.properties:  prisma.serverHost=192.168.1.50   o usa la variable PRISMA_SERVER_HOST.

/** Se vuelve a evaluar en cada build (también con configuration cache), así detecta cambios de red. */
abstract class LanIpSource : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String {
        // IP de la interfaz con salida a la red (no envía paquetes: un socket UDP solo elige la ruta).
        val routed = runCatching {
            DatagramSocket().use { socket ->
                socket.connect(InetAddress.getByName("8.8.8.8"), 80)
                (socket.localAddress as? Inet4Address)?.takeUnless { it.isAnyLocalAddress || it.isLoopbackAddress }?.hostAddress
            }
        }.getOrNull()
        // Sin internet: cualquier IP privada de la computadora (Wi-Fi o cable).
        val anyLan = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()
        return routed ?: anyLan ?: "localhost"
    }
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
val serverHost: Provider<String> = providers.environmentVariable("PRISMA_SERVER_HOST")
    .orElse(providers.gradleProperty("prisma.serverHost"))
    .orElse(provider { localProps.getProperty("prisma.serverHost")?.trim()?.takeIf(String::isNotEmpty) })
    .orElse(providers.of(LanIpSource::class) {})

val serverCertFile = rootProject.file("server/certs/prisma-cert.pem")

val generateServerConfig = tasks.register("generateServerConfig") {
    val host = serverHost
    val certFile = serverCertFile
    val outDir = layout.buildDirectory.dir("generated/serverConfig/kotlin")
    inputs.property("serverHost", host)
    inputs.files(certFile).optional()
    outputs.dir(outDir)
    doLast {
        // Certificate Pinning: las apps solo aceptan el certificado TLS de Prisma y su llave pública.
        // El pin es SHA-256 de la llave pública (SPKI), mismo formato que OkHttp CertificatePinner y curl --pinnedpubkey.
        require(certFile.isFile) {
            "Falta server/certs/prisma-cert.pem. Genera el certificado con server/certs/generate-cert.sh"
        }
        val pem = certFile.readText().trim()
        val cert = certFile.inputStream().use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
        val spkiSha256 = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(cert.publicKey.encoded),
        )
        val sanIps = cert.subjectAlternativeNames.orEmpty().map { it[1].toString() }
        if (host.get() !in sanIps) {
            logger.warn(
                "Prisma: el certificado TLS no incluye ${host.get()} (incluye $sanIps). " +
                    "Ejecuta server/certs/generate-cert.sh y vuelve a compilar.",
            )
        }
        val file = outDir.get().file("com/example/prisma/ServerConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.example.prisma
            |
            |// ARCHIVO GENERADO por app/shared/build.gradle.kts (generateServerConfig). No editar.
            |// IP de la computadora con el servidor, detectada al compilar.
            |const val SERVER_HOST = "${host.get()}"
            |
            |/** Puerto HTTPS (TLS 1.2/1.3). El puerto HTTP 8080 solo redirige a HTTPS. */
            |const val SERVER_PORT = 8443
            |
            |/** Pin SHA-256 de la llave pública del certificado del servidor (Certificate Pinning). */
            |const val SERVER_CERT_PIN = "sha256/$spkiSha256"
            |
            |/** Certificado público del servidor (autofirmado): única raíz de confianza de las apps. */
            |const val SERVER_CERT_PEM = ${"\"\"\""}
            |$pem
            |${"\"\"\""}
            |""".trimMargin(),
        )
        logger.lifecycle("Prisma: la app se conectará a https://${host.get()}:8443 (pin sha256/$spkiSha256)")
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
        iosTarget.compilations.getByName("main").defaultSourceSet.dependencies {
            implementation(libs.ktor.clientDarwin)
        }
    }
    
    js {
        browser()
        binaries.executable()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    
    android {
       namespace = "com.example.prisma.app.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        commonMain {
            kotlin.srcDir(generateServerConfig.map { it.outputs.files.singleFile })
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            // OkHttp: motor HTTP de Android con CertificatePinner (anclaje de certificado).
            implementation("io.ktor:ktor-client-okhttp:${libs.versions.ktor.get()}")
            // EncryptedSharedPreferences (AES-256) para las preferencias locales.
            implementation("androidx.security:security-crypto:1.1.0")
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.fragment)

            // Selector de archivos nativo de Android
            implementation("androidx.activity:activity-compose:1.13.0")
        }
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(compose.materialIconsExtended)
            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.clientContentNegotiation)
            implementation(libs.ktor.serializationKotlinxJson)
            implementation(libs.qrose)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
            implementation(libs.ktor.clientJs)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.clientJs)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}