package com.example.prisma

import com.example.prisma.auth.LoginRateLimiter
import com.example.prisma.auth.Passwords
import com.example.prisma.auth.SessionStore
import com.example.prisma.utils.TotpUtils
import java.util.UUID
import kotlin.test.*

class TotpUtilsTest {
    // Vector de prueba de RFC 6238 (SHA1): clave ASCII "12345678901234567890".
    private val rfcSecret = "12345678901234567890".toByteArray()

    @Test
    fun matchesRfc6238Vectors() {
        assertEquals("94287082", TotpUtils.generateTotp(rfcSecret, 59, digits = 8))
        assertEquals("07081804", TotpUtils.generateTotp(rfcSecret, 1111111109, digits = 8))
        assertEquals("287082", TotpUtils.generateTotp(rfcSecret, 59))
    }

    @Test
    fun base32RoundTrip() {
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", TotpUtils.base32Encode(rfcSecret))
        assertContentEquals(rfcSecret, TotpUtils.base32Decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"))
    }

    @Test
    fun generatedSecretsAreUniqueAndValid() {
        val a = TotpUtils.generateSecret()
        val b = TotpUtils.generateSecret()
        assertNotEquals(a, b)
        assertEquals(32, a.length)
        assertEquals(20, TotpUtils.base32Decode(a)!!.size)
    }

    @Test
    fun verifiesCurrentCodeAndRejectsGarbage() {
        val secret = TotpUtils.generateSecret()
        val now = System.currentTimeMillis() / 1000
        val code = TotpUtils.generateTotp(TotpUtils.base32Decode(secret)!!, now)
        assertTrue(TotpUtils.verifyCode(secret, code))
        assertFalse(TotpUtils.verifyCode(secret, "abcdef"))
        assertFalse(TotpUtils.verifyCode(secret, "12345"))
    }
}

class SessionStoreTest {
    private var now = 0L
    private val store = SessionStore(pendingTtlMillis = 1_000, sessionTtlMillis = 10_000, maxCodeAttempts = 3, clock = { now })
    private val user = UUID.randomUUID()

    @Test
    fun pendingTokenExpires() {
        val t = store.createPending(user)
        assertEquals(user, store.attemptPending(t))
        now += 1_001
        assertNull(store.attemptPending(t))
    }

    @Test
    fun pendingTokenLimitsAttempts() {
        val t = store.createPending(user)
        repeat(3) { assertEquals(user, store.attemptPending(t)) }
        assertNull(store.attemptPending(t))
    }

    @Test
    fun sessionsAreRandomExpireAndRevoke() {
        val a = store.createSession(user)
        val b = store.createSession(user)
        assertNotEquals(a, b)
        assertEquals(user, store.sessionUser(a))
        store.revoke(a)
        assertNull(store.sessionUser(a))
        now += 10_001
        assertNull(store.sessionUser(b))
    }
}

class LoginRateLimiterTest {
    @Test
    fun blocksAfterMaxFailuresAndUnblocksAfterWindow() {
        var now = 0L
        val limiter = LoginRateLimiter(maxFailures = 2, windowMillis = 1_000, clock = { now })
        limiter.recordFailure("a@b.com")
        assertFalse(limiter.isBlocked("a@b.com"))
        limiter.recordFailure("a@b.com")
        assertTrue(limiter.isBlocked("a@b.com"))
        now += 1_001
        assertFalse(limiter.isBlocked("a@b.com"))
    }
}

class PasswordsTest {
    @Test
    fun bcryptAndLegacyPlaintext() {
        val hash = Passwords.hash("secreta123")
        assertTrue(Passwords.verify("secreta123", hash))
        assertFalse(Passwords.verify("otra", hash))
        // Enviar el hash como contraseña ya NO permite entrar.
        assertFalse(Passwords.verify(hash, hash))
        // Contraseñas heredadas en texto plano siguen funcionando (y se migran al entrar).
        assertTrue(Passwords.verify("legacy", "legacy"))
        assertFalse(Passwords.verify("cualquiera", null))
    }
}
