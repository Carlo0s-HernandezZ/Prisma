package com.example.prisma

import kotlin.js.JsModule

// Registra las zonas horarias para Kotlin/Wasm.
@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule

private val jsJodaTz = JsJodaTimeZoneModule