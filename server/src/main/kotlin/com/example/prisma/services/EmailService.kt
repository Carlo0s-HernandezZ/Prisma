package com.example.prisma.services

import io.ktor.server.application.Application
import io.ktor.server.application.log
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties

/**
 * Envío de correos (códigos 2FA y de recuperación de contraseña).
 *
 * Configuración (las variables de entorno tienen prioridad):
 *  - SMTP_HOST (por defecto smtp.gmail.com), SMTP_PORT (587), SMTP_USER, SMTP_PASSWORD, SMTP_FROM.
 *  - O bien el archivo `server/smtp.properties` (ignorado por git) con las mismas llaves.
 *    Ver `server/smtp.properties.example`.
 *
 * Con Gmail, SMTP_PASSWORD debe ser una "contraseña de aplicación", no la contraseña normal.
 *
 * Si no hay credenciales, el correo se SIMULA: el mensaje se escribe en la consola del servidor
 * (solo para desarrollo).
 */
object EmailService {

    private val fileConfig: Properties by lazy {
        val props = Properties()
        // `./gradlew :server:run` se ejecuta con la carpeta server/ como directorio de trabajo.
        listOf(File("smtp.properties"), File("server/smtp.properties"))
            .firstOrNull(File::isFile)
            ?.inputStream()?.use(props::load)
        props
    }

    private fun setting(key: String): String? =
        System.getenv(key)?.takeIf(String::isNotBlank)
            ?: fileConfig.getProperty(key)?.trim()?.takeIf(String::isNotEmpty)

    val isConfigured: Boolean
        get() = !setting("SMTP_USER").isNullOrBlank() && !setting("SMTP_PASSWORD").isNullOrBlank()

    /** Avisa al arrancar si los códigos se enviarán de verdad o solo se mostrarán en consola. */
    fun logStartupStatus(application: Application) {
        if (isConfigured) {
            application.log.info("Correo SMTP configurado: ${setting("SMTP_USER")} vía ${setting("SMTP_HOST") ?: "smtp.gmail.com"}.")
        } else {
            application.log.warn(
                "SMTP NO configurado: los códigos 2FA NO se enviarán por correo, solo se mostrarán en esta consola. " +
                    "Define SMTP_USER y SMTP_PASSWORD o crea server/smtp.properties.",
            )
        }
    }

    /**
     * Envía el correo fuera del hilo de peticiones. Devuelve true si se envió (o se simuló por falta
     * de configuración) y false si el servidor SMTP lo rechazó o no respondió.
     */
    suspend fun sendEmail(to: String, subject: String, body: String, application: Application? = null): Boolean =
        withContext(Dispatchers.IO) { send(to, subject, body, application) }

    private fun send(to: String, subject: String, body: String, application: Application?): Boolean {
        val host = setting("SMTP_HOST") ?: "smtp.gmail.com"
        val port = setting("SMTP_PORT") ?: "587"
        val username = setting("SMTP_USER") ?: ""
        // Google muestra las contraseñas de aplicación con espacios; SMTP las espera sin ellos.
        val password = (setting("SMTP_PASSWORD") ?: "").replace(" ", "")
        val from = setting("SMTP_FROM") ?: username

        if (username.isBlank() || password.isBlank()) {
            val logMsg = "\n================ EMAIL SIMULADO (SMTP no configurado) ================\n" +
                "PARA: $to\nASUNTO: $subject\nMENSAJE:\n$body\n" +
                "======================================================================"
            application?.log?.warn(logMsg) ?: println(logMsg)
            return true
        }

        return try {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
                put("mail.smtp.host", host)
                put("mail.smtp.port", port)
                put("mail.smtp.ssl.trust", host)
                // Sin tiempos límite, un SMTP que no responde deja el login colgado hasta que la app se rinde.
                put("mail.smtp.connectiontimeout", "8000")
                put("mail.smtp.timeout", "8000")
                put("mail.smtp.writetimeout", "8000")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(from, "Prisma", "UTF-8"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                setSubject(subject, "UTF-8")
                setText(body, "UTF-8")
            }

            Transport.send(message)
            application?.log?.info("Correo enviado a $to")
            true
        } catch (e: Exception) {
            // No se registra el cuerpo: contiene códigos de verificación/recuperación.
            application?.log?.error("Error al enviar correo a $to (asunto: $subject): ${e.message}", e)
                ?: println("[ERROR ENVIO CORREO] Falló SMTP para $to (asunto: $subject): ${e.message}")
            false
        }
    }
}
