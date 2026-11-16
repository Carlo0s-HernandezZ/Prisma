package com.example.prisma.http

/**
 * Validación por **lista blanca** de los campos de texto libres (OWASP: "allow-list input validation").
 *
 * Además del largo, solo se aceptan los caracteres esperados para cada campo. Quedan fuera, entre
 * otros, `<` `>` `"` `` ` `` `{` `}` `;` y los caracteres de control: así un valor como
 * `<script>alert(1)</script>` o `' OR 1=1 --` se rechaza con 400 antes de llegar a la base de datos.
 */
object TextValidation {
    /** Nombres de personas: letras (con acentos y ñ), espacio, apóstrofo, guion y punto. */
    private val PERSON_NAME = Regex("^[\\p{L}][\\p{L} .'\\-]*$")

    /** Etiquetas (banco, alias de tarjeta, nombre de ingreso): letras, números y puntuación básica. */
    private val LABEL = Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N} .,()/#&+\\-]*$")

    fun isPersonName(value: String): Boolean = PERSON_NAME.matches(value)

    fun isLabel(value: String): Boolean = LABEL.matches(value)
}
