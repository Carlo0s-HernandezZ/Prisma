package com.example.prisma.config

import java.io.File
import java.util.Properties

/**
 * Configuración y secretos del servidor.
 *
 * Orden de prioridad: variable de entorno → archivo `server/.env` → `server/smtp.properties`.
 * Los archivos `.env` y `smtp.properties` están en `.gitignore`: los secretos (llave JWT, llave AES,
 * contraseña de la base de datos, API key de Gemini, contraseña SMTP) nunca se suben al repositorio.
 * La plantilla sin secretos es `server/.env.example`.
 */
object AppConfig {

    private val fileValues: Properties by lazy {
        val props = Properties()
        // `./gradlew :server:run` usa server/ como directorio de trabajo; desde la raíz es server/...
        for (name in listOf("smtp.properties", ".env")) {
            listOf(File(name), File("server/$name"))
                .firstOrNull(File::isFile)
                ?.reader(Charsets.UTF_8)?.use { reader ->
                    // Formato KEY=valor; se ignoran comentarios y comillas opcionales.
                    reader.readLines()
                        .map(String::trim)
                        .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
                        .forEach { line ->
                            val key = line.substringBefore('=').trim()
                            val value = line.substringAfter('=').trim().removeSurrounding("\"")
                            props.setProperty(key, value) // .env se lee al final: tiene prioridad sobre smtp.properties
                        }
                }
        }
        props
    }

    fun get(key: String): String? =
        System.getenv(key)?.takeIf(String::isNotBlank)
            ?: fileValues.getProperty(key)?.takeIf(String::isNotBlank)

    fun get(key: String, default: String): String = get(key) ?: default

    fun int(key: String, default: Int): Int = get(key)?.toIntOrNull() ?: default

    /**
     * Entorno de ejecución. En `production` (valor por defecto) no se muestran trazas de error,
     * no se imprimen correos simulados y los logs no incluyen datos personales.
     * Solo con PRISMA_ENV=development se habilitan las ayudas de depuración.
     */
    val isDevelopment: Boolean get() = get("PRISMA_ENV", "production").equals("development", ignoreCase = true)
}
