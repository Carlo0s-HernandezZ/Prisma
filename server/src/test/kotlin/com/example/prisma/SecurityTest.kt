package com.example.prisma

import com.example.prisma.auth.SessionStore
import com.example.prisma.http.CSRF_HEADER
import com.example.prisma.http.SESSION_COOKIE
import com.example.prisma.http.TextValidation
import com.example.prisma.http.UploadValidation
import com.example.prisma.security.AesGcmCipher
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import java.util.Base64
import java.util.UUID
import kotlin.test.*

/**
 * Pruebas de la Actividad 3.2 – Codificación Segura (no requieren PostgreSQL).
 * Cada prueba documenta el control de seguridad que verifica.
 */
class SecurityTest {

    private fun withApp(
        sessions: SessionStore = SessionStore(jwtSecret = "clave-de-prueba-de-al-menos-32-caracteres!!"),
        enforceHttps: Boolean = false,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            module(initDatabase = false, sessions = sessions, enforceHttps = enforceHttps)
            routing { get("/prueba-error") { error("detalle interno: password=secreta en com.example.Clase") } }
        }
        block()
    }

    // ------------------------------------------------------------------ Parte 3: HTTPS y cabeceras

    @Test
    fun httpIsRedirectedToHttps() = withApp(enforceHttps = true) {
        val noRedirect = createClient { followRedirects = false }
        val response = noRedirect.get("/api/v1/incomes")
        assertEquals(HttpStatusCode.MovedPermanently, response.status)
        assertTrue(response.headers[HttpHeaders.Location]!!.startsWith("https://"), "Location debe ser https")
    }

    @Test
    fun securityHeadersArePresent() = withApp {
        val headers = client.get("/").headers
        assertEquals("nosniff", headers["X-Content-Type-Options"])
        assertEquals("DENY", headers["X-Frame-Options"])
        assertContains(headers["Content-Security-Policy"].orEmpty(), "default-src 'none'")
        assertContains(headers["Content-Security-Policy"].orEmpty(), "frame-ancestors 'none'")
        assertEquals("no-referrer", headers["Referrer-Policy"])
        assertEquals("Prisma", headers[HttpHeaders.Server], "no debe revelar framework ni versión")
    }

    // ------------------------------------------------------------------ Parte 1: errores sin trazas

    @Test
    fun internalErrorsDoNotLeakStackTraces() = withApp {
        val response = client.get("/prueba-error")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        val body = response.bodyAsText()
        assertContains(body, "internal_error")
        assertFalse("password" in body || "com.example" in body || "Exception" in body || "at " in body, body)
    }

    // ------------------------------------------------------------------ Parte 4: JWT

    @Test
    fun accessTokenIsSignedJwtAndTamperingIsRejected() {
        val store = SessionStore(jwtSecret = "clave-de-prueba-de-al-menos-32-caracteres!!")
        val user = UUID.randomUUID()
        val jwt = store.createSession(user)

        val parts = jwt.split('.')
        assertEquals(3, parts.size, "un JWT tiene encabezado.carga.firma")
        val header = String(Base64.getUrlDecoder().decode(parts[0]))
        assertContains(header, "\"HS256\"")
        assertEquals(user, store.sessionUser(jwt))

        // Cambiar el sujeto (otro usuario) sin la llave invalida la firma.
        val forgedPayload = String(Base64.getUrlDecoder().decode(parts[1]))
            .replace(user.toString(), UUID.randomUUID().toString())
        val forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(forgedPayload.toByteArray()) + "." + parts[2]
        assertNull(store.sessionUser(forged))

        // Un JWT válido firmado con OTRA llave tampoco sirve.
        val other = SessionStore(jwtSecret = "otra-clave-distinta-de-al-menos-32-caracteres")
        assertNull(store.sessionUser(other.createSession(user)))
    }

    @Test
    fun logoutRevokesJwtEvenIfSignatureIsValid() {
        val store = SessionStore(jwtSecret = "clave-de-prueba-de-al-menos-32-caracteres!!")
        val jwt = store.createSession(UUID.randomUUID())
        assertNotNull(store.sessionUser(jwt))
        store.revoke(jwt)
        assertNull(store.sessionUser(jwt))
    }

    @Test
    fun expiredJwtIsRejected() {
        var now = 1_800_000_000_000L
        val store = SessionStore(clock = { now }, jwtSecret = "clave-de-prueba-de-al-menos-32-caracteres!!")
        val jwt = store.createSession(UUID.randomUUID())
        now += 13 * 60 * 60_000L // la sesión dura 12 h
        assertNull(store.sessionUser(jwt))
    }

    // ------------------------------------------------------------------ Parte 2: CSRF con cookie de sesión

    @Test
    fun cookieAuthenticatedWritesRequireCsrfToken() {
        val sessions = SessionStore(jwtSecret = "clave-de-prueba-de-al-menos-32-caracteres!!")
        val jwt = sessions.createSession(UUID.randomUUID())
        val csrf = sessions.sessionInfo(jwt)!!.csrfToken
        withApp(sessions = sessions) {
            // Cuerpo inválido a propósito: si la autenticación pasa, el endpoint responde 400 (no toca la BD).
            suspend fun post(withCsrf: String?) = client.post("/api/v1/ai/payroll/analyze") {
                header(HttpHeaders.Cookie, "$SESSION_COOKIE=$jwt")
                withCsrf?.let { header(CSRF_HEADER, it) }
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            val sinToken = post(null)
            assertEquals(HttpStatusCode.Forbidden, sinToken.status)
            assertContains(sinToken.bodyAsText(), "csrf_invalid")
            assertEquals(HttpStatusCode.Forbidden, post("token-inventado").status)
            assertEquals(HttpStatusCode.BadRequest, post(csrf).status, "con el token correcto pasa la autenticación")
        }
    }

    @Test
    fun bearerAuthenticatedRequestsDoNotNeedCsrf() {
        val sessions = SessionStore(jwtSecret = "clave-de-prueba-de-al-menos-32-caracteres!!")
        val jwt = sessions.createSession(UUID.randomUUID())
        withApp(sessions = sessions) {
            val response = client.post("/api/v1/ai/payroll/analyze") {
                bearerAuth(jwt) // el navegador nunca agrega esta cabecera por sí solo
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    // ------------------------------------------------------------------ Parte 2: listas blancas (XSS / inyección)

    @Test
    fun textWhitelistRejectsScriptsAndSqlPayloads() {
        val malicious = listOf(
            "<script>alert(1)</script>",
            "\"><img src=x onerror=alert(1)>",
            "' OR '1'='1",
            "x'; DROP TABLE usuarios; --",
            "{{7*7}}",
            "Banco\u0000Nulo",
        )
        for (value in malicious) {
            assertFalse(TextValidation.isLabel(value), "debe rechazar etiqueta: $value")
            assertFalse(TextValidation.isPersonName(value), "debe rechazar nombre: $value")
        }
        assertTrue(TextValidation.isLabel("BBVA Oro #2 (crédito)"))
        assertTrue(TextValidation.isPersonName("María José O'Neil-Núñez"))
    }

    @Test
    fun uploadWhitelistChecksRealContentAndSanitizesName() {
        val pdf = "%PDF-1.7\n...".toByteArray()
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0)
        val html = "<html><script>alert(1)</script></html>".toByteArray()
        val exe = byteArrayOf(0x4D, 0x5A, 0x90.toByte(), 0)

        assertEquals("application/pdf", UploadValidation.detectMime(pdf))
        assertEquals("image/png", UploadValidation.detectMime(png))
        assertNull(UploadValidation.detectMime(html), "HTML disfrazado no se acepta")
        assertNull(UploadValidation.detectMime(exe), "ejecutable no se acepta")

        assertEquals("estado_de_cuenta.pdf", UploadValidation.safeFileName("../../etc/estado<de>cuenta.exe", "application/pdf"))
        assertFalse('<' in UploadValidation.safeFileName("<script>.png", "image/png"))
        assertFalse('/' in UploadValidation.safeFileName("../../passwd", "application/pdf"))
    }

    // ------------------------------------------------------------------ Parte 1: AES-256-GCM en reposo

    @Test
    fun personalDataIsEncryptedWithAes256Gcm() {
        val cipher = AesGcmCipher(ByteArray(32) { it.toByte() })
        val a = cipher.encrypt("María")
        val b = cipher.encrypt("María")
        assertTrue(a.startsWith(AesGcmCipher.PREFIX))
        assertFalse("María" in a, "no debe contener el texto original")
        assertNotEquals(a, b, "IV aleatorio: el mismo valor se cifra distinto cada vez")
        assertEquals("María", cipher.decrypt(a))
        assertEquals("Dato heredado", cipher.decrypt("Dato heredado"))

        // Alterar un byte del valor guardado hace fallar la verificación GCM.
        val raw = Base64.getDecoder().decode(a.removePrefix(AesGcmCipher.PREFIX))
        raw[raw.size - 1] = (raw[raw.size - 1] + 1).toByte()
        val tampered = AesGcmCipher.PREFIX + Base64.getEncoder().encodeToString(raw)
        assertFails { cipher.decrypt(tampered) }

        assertFails { AesGcmCipher(ByteArray(16)) } // AES-128 no se acepta: solo 256 bits
    }
}

/** Regresión hallada por OWASP ZAP: una petición CORS rechazada provocaba un 500 en el plugin de cabeceras. */
class SecurityHeadersRegressionTest {
    @Test
    fun rejectedCorsRequestStillGetsHeadersWithoutError() = testApplication {
        application { module(initDatabase = false, enforceHttps = false) }
        val response = client.get("/api/v1/cards") { header(HttpHeaders.Origin, "https://sitio-malicioso.example") }
        assertNotEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        val preflight = client.options("/api/v1/cards") {
            header(HttpHeaders.Origin, "https://sitio-malicioso.example")
            header(HttpHeaders.AccessControlRequestMethod, "DELETE")
        }
        assertNotEquals(HttpStatusCode.InternalServerError, preflight.status)
    }
}
