package com.example.prisma.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import java.security.MessageDigest

object Passwords {
    private const val COST = 12

    /** Hash ficticio para que un correo inexistente tarde lo mismo que uno real (evita enumerar usuarios). */
    private val dummyHash by lazy { hash("prisma-dummy-password") }

    fun hash(password: String): String = BCrypt.withDefaults().hashToString(COST, password.toCharArray())

    fun isBcrypt(stored: String): Boolean = stored.startsWith("\$2")

    /**
     * Verifica la contraseña contra lo guardado en la base de datos.
     *
     * Si [stored] no es un hash BCrypt se trata como contraseña heredada en texto plano
     * (usuarios insertados a mano antes del registro con hash). El llamador debe
     * re-hashearla en cuanto el login sea exitoso; ver [isBcrypt].
     */
    fun verify(password: String, stored: String?): Boolean = when {
        stored == null -> {
            BCrypt.verifyer().verify(password.toCharArray(), dummyHash)
            false
        }
        isBcrypt(stored) -> BCrypt.verifyer().verify(password.toCharArray(), stored).verified
        else -> MessageDigest.isEqual(stored.toByteArray(), password.toByteArray())
    }
}
