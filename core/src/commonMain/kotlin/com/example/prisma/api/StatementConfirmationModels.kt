package com.example.prisma.api

import kotlinx.serialization.Serializable

@Serializable
data class StatementConfirmationRequest(
    val campos: StatementFields,
    val version: Int = 0,
    val confirmadoPorUsuario: Boolean = false,
)

@Serializable
data class ConfirmedStatementDto(
    val tarjetaId: String,
    val campos: StatementFields,
    val version: Int,
    val fechaConfirmacion: String,
)