package com.example.prisma.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.example.prisma.api.ConfirmedStatementDto
import com.example.prisma.cards.KtorStatementPaymentsRepository
import com.example.prisma.cards.StatementPaymentsViewModel

@Composable
fun StatementPaymentsSection(
    estado: ConfirmedStatementDto,
    token: String,
    onSessionExpired: () -> Unit,
    onReloadStatement: () -> Unit,
) {
    val period = estado.campos.periodo ?: return

    val viewModel = composeViewModel(
        key = "statement-payments-${estado.tarjetaId}-$period-$token",
    ) {
        StatementPaymentsViewModel(
            cardId = estado.tarjetaId,
            periodo = period,
            repository = KtorStatementPaymentsRepository(token),
        )
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSessionExpired by rememberUpdatedState(onSessionExpired)

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            currentOnSessionExpired()
        }
    }

    LaunchedEffect(estado.version) {
        val loadedVersion =
            viewModel.state.value.data?.estado?.version

        if (
            loadedVersion != null &&
            loadedVersion != estado.version
        ) {
            viewModel.reload()
        }
    }

    val data = state.data
    val busy = state.isLoading || state.isSaving

    val stale = data != null &&
            data.estado.version != estado.version

    val ready = data != null &&
            !busy &&
            !state.conflict &&
            !state.sessionExpired &&
            !stale

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider()

        Text(
            text = "Pagos aplicados",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = Prisma.colors.onSurface,
        )

        Text(
            text = "Registra cada pago una sola vez en el estado al que se aplicó. " +
                    "Usa la fecha de aplicación del banco, posterior al corte.",
            style = MaterialTheme.typography.bodySmall,
        )

        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = PrismaPalette.NaranjaPrisma,
            )
        }

        state.error?.let {
            Text(
                text = it,
                color = Prisma.colors.statusCritical,
            )
        }

        state.message?.let {
            Text(
                text = it,
                color = PrismaPalette.NaranjaAmbar,
            )
        }

        if (stale) {
            Text(
                "El estado de cuenta cambió. Actualízalo antes de revisar sus pagos.",
            )

            TextButton(
                onClick = {
                    viewModel.reload()
                    onReloadStatement()
                },
                enabled = !busy && state.draft == null,
            ) {
                Text("Actualizar estado de cuenta")
            }

            state.draft?.let { draft ->
                Text(
                    text = "Pagos por revisar, todavía sin guardar:",
                    fontWeight = FontWeight.SemiBold,
                )

                draft.forEach { payment ->
                    Text(
                        formatIsoDateTime(payment.fechaAplicacion) +
                                " · " +
                                formatMxn(payment.monto),
                    )
                }
            }
        }

        if (data != null && !stale) {
            if (data.version == 0) {
                Text(
                    text = "Aún no has confirmado los pagos de este estado. " +
                            "También puedes confirmar que todavía no has realizado ninguno.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (data.requiereRevision) {
                Text(
                    text = "El estado fue actualizado. Revisa y vuelve a confirmar sus pagos " +
                            "antes de calcular el faltante.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PrismaPalette.NaranjaAmbar,
                )
            }

            data.resumen?.let { summary ->
                Text(
                    text = "Estimación al " +
                            formatIsoDateTime(data.fechaReferencia),
                    fontWeight = FontWeight.SemiBold,
                )

                StatementPaymentRow(
                    "Total de pagos registrados",
                    summary.totalAplicado,
                )

                StatementPaymentRow(
                    "Aplicado hasta la fecha límite",
                    summary.aplicadoHastaFechaLimite,
                )

                StatementPaymentRow(
                    "Faltante estimado del mínimo",
                    summary.faltanteMinimo,
                )

                StatementPaymentRow(
                    "Faltante estimado del importe para no generar intereses",
                    summary.faltanteParaNoGenerarIntereses,
                )

                if (summary.plazoVencido) {
                    Text(
                        text = "Objetivos a la fecha límite, según los pagos registrados:",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    StatementPaymentRow(
                        "Mínimo que faltaba cubrir",
                        summary.faltanteMinimoAlVencimiento,
                    )

                    StatementPaymentRow(
                        "Importe para no generar intereses que faltaba cubrir",
                        summary.faltanteParaNoGenerarInteresesAlVencimiento,
                    )
                }

                summary.advertencias.forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = PrismaPalette.NaranjaAmbar,
                    )
                }
            }

            val editing = state.draft != null
            val payments = state.draft ?: data.pagos

            if (editing) {
                Text(
                    text = "Cambios por guardar",
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = "La estimación se actualizará al confirmar los pagos.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (payments.isEmpty()) {
                Text(
                    text = "La lista de pagos está vacía.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            payments.forEach { payment ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = formatIsoDateTime(payment.fechaAplicacion) +
                                " · " +
                                formatMxn(payment.monto),
                        fontWeight = FontWeight.Medium,
                    )

                    if (editing) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(
                                onClick = {
                                    viewModel.editPayment(payment.id)
                                },
                                enabled = ready && state.editor == null,
                            ) {
                                Text("Editar registro")
                            }

                            TextButton(
                                onClick = {
                                    viewModel.removePayment(payment.id)
                                },
                                enabled = ready && state.editor == null,
                            ) {
                                Text("Quitar registro")
                            }
                        }
                    }
                }
            }

            if (!editing) {
                Button(
                    onClick = viewModel::beginEdit,
                    enabled = ready,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrismaPalette.Dorado,
                        contentColor = PrismaPalette.Grafito,
                    ),
                ) {
                    Text(
                        text = "Revisar pagos registrados",
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                val editor = state.editor

                if (editor == null) {
                    OutlinedButton(
                        onClick = viewModel::addPayment,
                        enabled = ready && payments.size < 100,
                    ) {
                        Text("Agregar pago aplicado")
                    }
                } else {
                    Text(
                        text = "Registro del pago",
                        fontWeight = FontWeight.SemiBold,
                    )

                    PrismaDateField(
                        value = editor.fecha,
                        onValueChange = viewModel::onDateChange,
                        label = "Fecha de aplicación",
                        enabled = ready,
                        allowFuture = false,
                    )

                    OutlinedTextField(
                        value = editor.monto,
                        onValueChange = viewModel::onAmountChange,
                        label = {
                            Text("Monto aplicado (MXN)")
                        },
                        enabled = ready,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    editor.error?.let {
                        Text(
                            text = it,
                            color = Prisma.colors.statusCritical,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = viewModel::savePayment,
                            enabled = ready,
                        ) {
                            Text("Aceptar registro")
                        }

                        TextButton(
                            onClick = viewModel::closeEditor,
                            enabled = !busy,
                        ) {
                            Text("Cancelar registro")
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.acknowledged,
                        onCheckedChange = viewModel::setAcknowledged,
                        enabled = ready && editor == null,
                    )

                    Text(
                        text = "Revisé esta lista. Los pagos indicados ya se aplicaron a este estado " +
                                "y no están incluidos en sus importes al corte.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(
                    onClick = viewModel::save,
                    enabled = ready &&
                            editor == null &&
                            state.acknowledged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrismaPalette.Dorado,
                        contentColor = PrismaPalette.Grafito,
                    ),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = PrismaPalette.Grafito,
                        )
                    } else {
                        Text(
                            text = "Confirmar pagos",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                TextButton(
                    onClick = viewModel::cancelEdit,
                    enabled = !busy,
                ) {
                    Text("Descartar cambios de pagos")
                }
            }
        }

        if (
            (state.conflict || stale) &&
            state.draft != null
        ) {
            TextButton(
                onClick = viewModel::discardAndReload,
                enabled = !busy,
            ) {
                Text("Descartar cambios y recargar pagos")
            }
        } else if (state.draft == null) {
            TextButton(
                onClick = viewModel::reload,
                enabled = !busy,
            ) {
                Text("Recargar pagos")
            }
        }
    }
}

@Composable
private fun StatementPaymentRow(
    label: String,
    amount: String,
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
            text = formatMxn(amount),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}