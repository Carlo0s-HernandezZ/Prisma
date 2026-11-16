package com.example.prisma.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prisma.cards.StatementReaderViewModel
import com.example.prisma.components.SoftCard

@Composable
fun StatementReaderSection(
    viewModel: StatementReaderViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SoftCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Lectura del estado de cuenta",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )

            Text(
                text = "Selecciona un PDF o imágenes con los controles " +
                        "de carga de esta tarjeta. Agrega todas las páginas " +
                        "del mismo estado y pulsa Leer archivos.",
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text = "Hasta 12 páginas, 10 MB por archivo y " +
                        "20 MB en total. PDF, JPG o PNG.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (state.files.isEmpty()) {
                Text(
                    text = "Selecciona los archivos para comenzar.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            state.files.forEach { file ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = file.nombre,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    IconButton(
                        onClick = {
                            viewModel.removeFile(file.id)
                        },
                        enabled = !state.isReading,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Quitar archivo",
                        )
                    }
                }
            }

            Button(
                onClick = viewModel::read,
                enabled = state.files.isNotEmpty() &&
                        !state.isReading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrismaPalette.Dorado,
                    contentColor = PrismaPalette.Grafito,
                ),
            ) {
                if (state.isReading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = PrismaPalette.Grafito,
                    )
                } else {
                    Text(
                        text = "Leer archivos",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (state.isReading) {
                Text(
                    text = "Leyendo el documento. Las imágenes y " +
                            "los PDF escaneados pueden tardar más.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            state.error?.let {
                Text(
                    text = it,
                    color = Prisma.colors.statusCritical,
                )
            }

            if (state.files.isNotEmpty()) {
                TextButton(
                    onClick = viewModel::clear,
                    enabled = !state.isReading,
                ) {
                    Text("Limpiar selección")
                }
            }

            state.result?.let { result ->
                HorizontalDivider()

                Text(
                    text = "Lectura preliminar",
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = result.archivosLeidos.toString() +
                            " archivos · " +
                            result.paginasLeidas + " páginas",
                    style = MaterialTheme.typography.bodySmall,
                )

                val fields = result.campos

                StatementReadRow(
                    "Periodo",
                    fields.periodo,
                )

                StatementReadRow(
                    "Fecha de corte",
                    fields.fechaCorte?.let(::formatIsoDateTime),
                )

                StatementReadRow(
                    "Fecha límite de pago",
                    fields.fechaLimitePago?.let(::formatIsoDateTime),
                )

                StatementReadRow(
                    "Saldo total",
                    fields.saldoTotal?.let(::formatMxn),
                )

                StatementReadRow(
                    "Pago mínimo",
                    fields.pagoMinimo?.let(::formatMxn),
                )

                StatementReadRow(
                    "Para no generar intereses",
                    fields.pagoParaNoGenerarIntereses
                        ?.let(::formatMxn),
                )

                StatementReadRow(
                    "Tasa ordinaria anual",
                    fields.tasaInteresAnual?.let {
                        it + "% anual"
                    },
                )

                StatementReadRow(
                    "Mensualidades MSI del periodo",
                    fields.mensualidadesMsi?.let(::formatMxn),
                )

                result.advertencias.forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text(
                    text = "Estos datos todavía requieren revisión " +
                            "y no se utilizan para calcular pagos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PrismaPalette.NaranjaAmbar,
                )
            }
        }
    }
}

@Composable
private fun StatementReadRow(
    label: String,
    value: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            text = value ?: "Por confirmar",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}