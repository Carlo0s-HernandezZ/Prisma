import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Properties

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

val generateServerConfig = tasks.register("generateServerConfig") {
    val host = serverHost
    val outDir = layout.buildDirectory.dir("generated/serverConfig/kotlin")
    inputs.property("serverHost", host)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/example/prisma/ServerConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.example.prisma
            |
            |// ARCHIVO GENERADO por app/shared/build.gradle.kts (generateServerConfig). No editar.
            |// IP de la computadora con el servidor, detectada al compilar.
            |const val SERVER_HOST = "${host.get()}"
            |const val SERVER_PORT = 8080
            |""".trimMargin(),
        )
        logger.lifecycle("Prisma: la app se conectará al servidor en http://${host.get()}:8080")
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
            implementation(libs.ktor.clientCio)
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.fragment)
            implementation(libs.androidx.activity.compose)
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