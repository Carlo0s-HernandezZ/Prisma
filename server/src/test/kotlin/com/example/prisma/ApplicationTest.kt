package com.example.prisma

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

/** Pruebas que no requieren PostgreSQL (la base de datos no se inicializa). */
class ApplicationTest {

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(initDatabase = false) }
        block()
    }

    @Test
    fun rootResponds() = withApp {
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Prisma API", response.bodyAsText())
    }

    @Test
    fun protectedEndpointsRequireToken() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/user/profile").status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete("/api/v1/user/profile").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/reports/latest").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/incomes").status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete("/api/v1/incomes/00000000-0000-0000-0000-000000000000").status)
        for (path in listOf("enroll", "renew", "revoke")) {
            val r = client.post("/api/v1/auth/biometric/$path") {
                contentType(ContentType.Application.Json)
                setBody("""{"plataforma":"android","deviceId":"00000000-0000-0000-0000-000000000000"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, r.status, "biometric/$path sin token")
        }
        val forged = client.get("/api/v1/reports/latest") {
            header(HttpHeaders.Authorization, "Bearer prisma_access_token_123")
        }
        assertEquals(HttpStatusCode.Unauthorized, forged.status)
    }

    @Test
    fun emailQueryParamNoLongerGrantsAccess() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.delete("/api/v1/user/profile?email=victima@correo.com").status)
    }

    @Test
    fun verify2faWithUnknownTempTokenIsRejected() = withApp {
        val response = client.post("/api/v1/auth/verify-2fa") {
            contentType(ContentType.Application.Json)
            setBody("""{"tempToken":"inventado","code":"123456"}""")
        }
        assertEquals(HttpStatusCode.Gone, response.status)
    }

    @Test
    fun malformedJsonReturns400() = withApp {
        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("{no es json")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun registerRejectsWeakPasswordAndBadEmail() = withApp {
        val weak = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"a@b.com","password":"123"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, weak.status)
        assertContains(weak.bodyAsText(), "weak_password")

        val badEmail = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"sin-arroba","password":"12345678"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, badEmail.status)
    }
}
