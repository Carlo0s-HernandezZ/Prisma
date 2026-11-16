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
import com.example.prisma.api.StatementExtractionResponse
import com.example.prisma.cards.KtorStatementConfirmationRepository
import com.example.prisma.cards.StatementConfirmationViewModel
import com.example.prisma.components.SoftCard

@Composable
fun StatementConfirmationSection(
    cardId: String,
    token: String,
    extraction: StatementExtractionResponse?,
    onSessionExpired: () -> Unit,
) {
    val viewModel = composeViewModel(
        key = "statement-confirmation-$cardId-$token",
    ) {
        StatementConfirmationViewModel(
            cardId = cardId,
            repository = KtorStatementConfirmationRepository(token),
        )
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSessionExpired by rememberUpdatedState(onSessionExpired)

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            currentOnSessionExpired()
        }
    }

    val busy = state.isLoading || state.isSaving

    val ready = state.hasLoaded &&
            !busy &&
            !state.conflict &&
            !state.sessionExpired

    SoftCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Revisión y guardado",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Prisma.colors.onSurface,
            )

            Text(
                text = "Revisa los datos del documento antes de confirmarlos. " +
                        "El estado se guarda para esta tarjeta y su periodo.",
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

            if (state.draft == null) {
                if (state.records.isNotEmpty()) {
                    Text(
                        text = "Periodos guardados: " +
                                state.records.joinToString {
                                    it.campos.periodo.orEmpty()
                                },
                        style = MaterialTheme.typography.bodySmall,
                    )

                    PrismaMonthField(
                        value = state.selectedPeriod.orEmpty(),
                        onValueChange = viewModel::selectPeriod,
                        label = "Consultar periodo guardado",
                        enabled = !busy,
                    )

                    val saved = state.records.firstOrNull {
                        it.campos.periodo == state.selectedPeriod
                    }

                    if (saved != null) {
                        StatementPaymentsSection(
                            estado = saved,
                            token = token,
                            onSessionExpired = {
                                currentOnSessionExpired()
                            },
                            onReloadStatement = viewModel::reload,
                        )
                        val fields = saved.campos

                        ConfirmedStatementRow(
                            "Fecha de corte",
                            fields.fechaCorte?.let(::formatIsoDateTime),
                        )

                        ConfirmedStatementRow(
                            "Fecha límite de pago",
                            fields.fechaLimitePago?.let(::formatIsoDateTime),
                        )

                        ConfirmedStatementRow(
                            "Saldo total",
                            fields.saldoTotal?.let(::formatMxn),
                        )

                        ConfirmedStatementRow(
                            "Pago mínimo",
                            fields.pagoMinimo?.let(::formatMxn),
                        )

                        ConfirmedStatementRow(
                            "Para no generar intereses",
                            fields.pagoParaNoGenerarIntereses?.let(::formatMxn),
                        )

                        ConfirmedStatementRow(
                            "Tasa ordinaria anual",
                            fields.tasaInteresAnual?.let {
                                "$it% anual"
                            },
                        )

                        ConfirmedStatementRow(
                            "Mensualidades MSI del periodo",
                            fields.mensualidadesMsi?.let(::formatMxn),
                        )

                        TextButton(
                            onClick = viewModel::editSaved,
                            enabled = ready,
                        ) {
                            Text("Editar estado guardado")
                        }
                    } else {
                        Text(
                            "No hay un estado guardado para ese periodo.",
                        )
                    }
                } else if (state.hasLoaded) {
                    Text(
                        "Esta tarjeta todavía no tiene estados confirmados.",
                    )
                }

                Button(
                    onClick = {
                        extraction?.let(viewModel::startReview)
                    },
                    enabled = ready && extraction != null,
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
                        text = "Revisar esta lectura",
                        fontWeight = FontWeight.Bold,
                    )
                }

                TextButton(
                    onClick = viewModel::reload,
                    enabled = !busy,
                ) {
                    Text("Recargar estados guardados")
                }
            } else {
                val draft = state.draft!!
                val editable = ready

                val replacing = state.records.any {
                    it.campos.periodo == draft.periodo
                }

                HorizontalDivider()

                Text(
                    text = "Datos por confirmar",
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = "El periodo corresponde al mes de la fecha de corte. " +
                            "Puedes dejar saldo total, tasa y MSI vacíos " +
                            "si no están confirmados.",
                    style = MaterialTheme.typography.bodySmall,
                )

                if (replacing) {
                    Text(
                        text = "Al guardar actualizarás el estado existente de este periodo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrismaPalette.NaranjaAmbar,
                    )
                }

                PrismaMonthField(
                    value = draft.periodo.orEmpty(),
                    onValueChange = { value ->
                        viewModel.edit {
                            it.copy(periodo = value)
                        }
                    },
                    label = "Periodo del estado",
                    enabled = editable,
                )

                PrismaDateField(
                    value = draft.fechaCorte.orEmpty(),
                    onValueChange = { value ->
                        viewModel.edit {
                            it.copy(fechaCorte = value)
                        }
                    },
                    label = "Fecha de corte",
                    enabled = editable,
                )

                PrismaDateField(
                    value = draft.fechaLimitePago.orEmpty(),
                    onValueChange = { value ->
                        viewModel.edit {
                            it.copy(fechaLimitePago = value)
                        }
                    },
                    label = "Fecha límite de pago",
                    enabled = editable,
                )

                StatementConfirmationNumber(
                    label = "Pago mínimo (MXN)",
                    value = draft.pagoMinimo,
                    enabled = editable,
                ) { value ->
                    viewModel.edit {
                        it.copy(pagoMinimo = value)
                    }
                }

                StatementConfirmationNumber(
                    label = "Para no generar intereses (MXN)",
                    value = draft.pagoParaNoGenerarIntereses,
                    enabled = editable,
                ) { value ->
                    viewModel.edit {
                        it.copy(pagoParaNoGenerarIntereses = value)
                    }
                }

                StatementConfirmationNumber(
                    label = "Saldo total (MXN, opcional)",
                    value = draft.saldoTotal,
                    enabled = editable,
                ) { value ->
                    viewModel.edit {
                        it.copy(saldoTotal = value)
                    }
                }

                StatementConfirmationNumber(
                    label = "Tasa ordinaria anual (%, opcional)",
                    value = draft.tasaInteresAnual,
                    enabled = editable,
                ) { value ->
                    viewModel.edit {
                        it.copy(tasaInteresAnual = value)
                    }
                }

                StatementConfirmationNumber(
                    label = "Mensualidades MSI del periodo (MXN, opcional)",
                    value = draft.mensualidadesMsi,
                    enabled = editable,
                ) { value ->
                    viewModel.edit {
                        it.copy(mensualidadesMsi = value)
                    }
                }

                Text(
                    text = "Importes: usa punto decimal, por ejemplo 1375.00. " +
                            "Un campo vacío significa pendiente; " +
                            "escribe 0 solo si lo verificaste.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.acknowledged,
                        onCheckedChange = viewModel::setAcknowledged,
                        enabled = editable,
                    )

                    Text(
                        text = "Revisé los importes y las fechas contra el documento. " +
                                "Los campos opcionales vacíos quedan pendientes.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(
                    onClick = viewModel::save,
                    enabled = editable && state.acknowledged,
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
                            text = "Confirmar y guardar",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                TextButton(
                    onClick = viewModel::cancelEdit,
                    enabled = !busy,
                ) {
                    Text("Descartar edición")
                }

                if (state.conflict) {
                    TextButton(
                        onClick = viewModel::discardAndReload,
                        enabled = !busy,
                    ) {
                        Text("Descartar cambios y recargar")
                    }
                }
            }

            Text(
                text = "Los importes guardados corresponden al documento " +
                        "y su periodo; pueden existir pagos posteriores.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatementConfirmationNumber(
    label: String,
    value: String?,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = onValueChange,
        label = {
            Text(label)
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConfirmedStatementRow(
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
            text = value ?: "Pendiente",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}