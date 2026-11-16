package com.example.prisma.api

import kotlinx.serialization.Serializable

@Serializable
data class StatementFields(
    val periodo: String? = null,
    val fechaCorte: String? = null,
    val fechaLimitePago: String? = null,
    val saldoTotal: String? = null,
    val pagoMinimo: String? = null,
    val pagoParaNoGenerarIntereses: String? = null,
    val tasaInteresAnual: String? = null,
    val mensualidadesMsi: String? = null,
)

@Serializable
data class StatementExtractionResponse(
    val campos: StatementFields,
    val paginasLeidas: Int,
    val archivosLeidos: Int,
    val camposPendientes: List<String>,
    val advertencias: List<String>,
    val paginasPorCampo: Map<String, Int> = emptyMap(),
)