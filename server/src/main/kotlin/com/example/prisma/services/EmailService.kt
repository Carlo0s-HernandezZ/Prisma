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
import java.util.Properties

object EmailService {
    fun sendEmail(to: String, subject: String, body: String, application: Application? = null) {
        val host = System.getenv("SMTP_HOST") ?: "smtp.gmail.com"
        val port = System.getenv("SMTP_PORT") ?: "587"
        val username = System.getenv("SMTP_USER") ?: ""
        val password = System.getenv("SMTP_PASSWORD") ?: ""
        val from = System.getenv("SMTP_FROM") ?: username

        if (username.isBlank() || password.isBlank()) {
            val logMsg = "\n================ EMAIL SIMULADO ================\nPARA: $to\nASUNTO: $subject\nMENSAJE:\n$body\n================================================"
            if (application != null) {
                application.log.warn(logMsg)
            } else {
                println(logMsg)
            }
            return
        }

        try {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", host)
                put("mail.smtp.port", port)
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(from))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                setSubject(subject, "UTF-8")
                setText(body, "UTF-8")
            }

            Transport.send(message)
            application?.log?.info("Correo enviado exitosamente a $to")
        } catch (e: Exception) {
            application?.log?.error("Error al enviar correo a $to: ${e.message}", e)
            println("\n[ERROR ENVIO CORREO] Falló SMTP, código/mensaje para $to:\nAsunto: $subject\n$body\n")
        }
    }
}
