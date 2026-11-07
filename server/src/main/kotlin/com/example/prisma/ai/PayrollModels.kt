
package com.example.prisma.ai

import kotlinx.serialization.Serializable

@Serializable
data class PayrollRequest(
    val salarioBruto: Double,
    val isr: Double,
    val imss: Double,
    val otrasDeducciones: Double = 0.0,
    val salarioNeto: Double,
    val periodo: String
)

@Serializable
data class PayrollAnalysis(
    val resumen: String,
    val percepciones: Double,
    val deducciones: Double,
    val salarioNeto: Double,
    val observaciones: List<String>,
    val recomendaciones: List<String>,
    val requiereRevision: Boolean,
    val salarioNetoDeclarado: Double,
    val diferencia: Double,
    val existeDiferencia: Boolean
)
