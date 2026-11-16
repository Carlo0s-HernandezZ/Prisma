package com.example.prisma

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsModule
import kotlin.js.JsNonModule

// Importar este módulo registra las zonas horarias en js-joda.
@JsModule("@js-joda/timezone")
@JsNonModule
external object JsJodaTimeZoneModule

// Conserva la inicialización al generar y optimizar el JavaScript.
@OptIn(ExperimentalJsExport::class)
@JsExport
val jsJodaTz = JsJodaTimeZoneModule