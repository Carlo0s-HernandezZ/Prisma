package com.example.prisma.services

import com.example.prisma.config.AppConfig
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
import java.util.Properties

/**
 * Envío de correos (códigos 2FA y de recuperación de contraseña).
 *
 * Configuración (las variables de entorno tienen prioridad):
 *  - SMTP_HOST (por defecto smtp.gmail.com), SMTP_PORT (587), SMTP_USER, SMTP_PASSWORD, SMTP_FROM.
 *  - O bien `server/.env` / `server/smtp.properties` (ignorados por git) con las mismas llaves (ver [AppConfig]).
 *    Ver `server/smtp.properties.example`.
 *
 * Con Gmail, SMTP_PASSWORD debe ser una "contraseña de aplicación", no la contraseña normal.
 *
 * Si no hay credenciales y PRISMA_ENV=development, el correo se SIMULA en la consola del servidor.
 * En producción no se simula: el envío falla y nunca se escribe el código en los logs.
 */
object EmailService {

    private fun setting(key: String): String? = AppConfig.get(key)

    /** Oculta el correo en los logs: "carlos@gmail.com" → "c***@gmail.com". */
    internal fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        return if (at <= 0) "***" else email.first() + "***" + email.substring(at)
    }

    val isConfigured: Boolean
        get() = !setting("SMTP_USER").isNullOrBlank() && !setting("SMTP_PASSWORD").isNullOrBlank()

    /** Avisa al arrancar si los códigos se enviarán de verdad o solo se mostrarán en consola. */
    fun logStartupStatus(application: Application) {
        if (isConfigured) {
            application.log.info("Correo SMTP configurado: ${maskEmail(setting("SMTP_USER").orEmpty())} vía ${setting("SMTP_HOST") ?: "smtp.gmail.com"}.")
        } else {
            application.log.warn(
                "SMTP NO configurado: los códigos 2FA NO se enviarán por correo (solo con PRISMA_ENV=development se muestran en consola). " +
                    "Define SMTP_USER y SMTP_PASSWORD en server/.env.",
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
            // En producción nunca se escribe el contenido del correo (lleva códigos de acceso) en los logs.
            if (!AppConfig.isDevelopment) {
                application?.log?.error("SMTP no configurado: no se pudo enviar el correo a ${maskEmail(to)}.")
                return false
            }
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
            application?.log?.info("Correo enviado a ${maskEmail(to)}")
            true
        } catch (e: Exception) {
            // No se registra el cuerpo: contiene códigos de verificación/recuperación.
            application?.log?.error("Error al enviar correo a ${maskEmail(to)} (asunto: $subject): ${e::class.simpleName}")
            false
        }
    }
}
