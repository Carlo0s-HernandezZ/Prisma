package com.example.prisma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prisma.api.BudgetMovementType
import com.example.prisma.budget.BudgetViewModel
import com.example.prisma.components.SoftCard
import com.example.prisma.components.bottomNavBarSpace

@Composable
fun AnalysisScreen(viewModel: BudgetViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val busy = state.isLoading || state.isSaving

    val editable = state.hasLoaded &&
            !busy &&
            !state.conflict

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Prisma.colors.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .screenContainer()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Análisis",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )

            Text(
                text = "Presupuesto para tus tarjetas",
                style = MaterialTheme.typography.bodyLarge,
            )

            PrismaMonthField(
                value = state.periodo,
                onValueChange = viewModel::selectPeriod,
                label = "Mes del presupuesto",
                enabled = !busy,
            )

            TextButton(
                onClick = viewModel::reload,
                enabled = !busy,
            ) {
                Text("Recargar presupuesto")
            }

            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
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
                    color = Prisma.colors.statusHealthy,
                )
            }

            BudgetPanel("Dinero y reserva") {
                PrismaDateField(
                    value = state.fechaBase,
                    onValueChange = viewModel::onDateChange,
                    label = "Fecha de referencia",
                    enabled = editable,
                    allowFuture = false,
                    supportingText =
                        "Confirma el saldo que tenías en esta fecha.",
                )

                BudgetAmountField(
                    label = "Saldo disponible en esa fecha",
                    value = state.saldo,
                    onChange = viewModel::onBalanceChange,
                    enabled = editable,
                )

                Text(
                    text = "Incluye la reserva dentro de este saldo. " +
                            "Los ingresos ya cobrados no se vuelven a agregar.",
                    style = MaterialTheme.typography.bodySmall,
                )

                BudgetAmountField(
                    label = "Reserva que deseas conservar",
                    value = state.reserva,
                    onChange = viewModel::onReserveChange,
                    enabled = editable,
                )
            }

            BudgetPanel("Movimientos pendientes del mes") {
                Text(
                    text = "Agrega solo ingresos confirmados aún por " +
                            "cobrar y gastos esenciales aún por pagar, desde " +
                            "la fecha de referencia. Estos movimientos " +
                            "pertenecen al presupuesto de este mes. No " +
                            "incluyas pagos de tarjetas que calcularemos después.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.openMovement(
                                BudgetMovementType.INGRESO,
                            )
                        },
                        enabled = editable &&
                                state.fechaBase.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text("Agregar ingreso")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.openMovement(
                                BudgetMovementType.GASTO,
                            )
                        },
                        enabled = editable &&
                                state.fechaBase.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text("Agregar gasto")
                    }
                }

                if (state.movimientos.isEmpty()) {
                    Text("Sin movimientos pendientes registrados.")
                }

                state.movimientos
                    .sortedBy { it.fecha }
                    .forEach { movement ->
                        HorizontalDivider(
                            color = Prisma.colors.onSurface.copy(
                                alpha = 0.08f,
                            ),
                        )

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = movement.concepto,
                                    fontWeight = FontWeight.SemiBold,
                                )

                                Text(
                                    text = formatIsoDateTime(
                                        movement.fecha,
                                    ),
                                    style =
                                        MaterialTheme.typography.bodySmall,
                                )

                                Text(
                                    text = (
                                            if (
                                                movement.tipo ==
                                                BudgetMovementType.INGRESO
                                            ) {
                                                "+ "
                                            } else {
                                                "− "
                                            }
                                            ) + formatMxn(movement.monto),
                                    color = if (
                                        movement.tipo ==
                                        BudgetMovementType.INGRESO
                                    ) {
                                        Prisma.colors.statusHealthy
                                    } else {
                                        Prisma.colors.onSurface
                                    },
                                )
                            }

                            IconButton(
                                onClick = {
                                    viewModel.editMovement(movement.id)
                                },
                                enabled = editable,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription =
                                        "Editar movimiento",
                                )
                            }

                            IconButton(
                                onClick = {
                                    viewModel.removeMovement(
                                        movement.id,
                                    )
                                },
                                enabled = editable,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription =
                                        "Quitar movimiento",
                                    tint = Prisma.colors.statusCritical,
                                )
                            }
                        }
                    }
            }

            Button(
                onClick = viewModel::save,
                enabled = editable &&
                        (state.dirty || state.version == 0),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
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
                        text = "Guardar y calcular",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (state.dirty) {
                Text(
                    text = "Guarda los cambios para actualizar el cálculo.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            state.resumen
                ?.takeIf { !state.dirty }
                ?.let { summary ->
                    BudgetPanel("Capacidad para tarjetas") {
                        Text(
                            text = "Saldo confirmado al " +
                                    formatIsoDateTime(state.fechaBase),
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Text(
                            text = "Actualiza la fecha y el saldo cuando " +
                                    "cambie tu dinero disponible.",
                            style = MaterialTheme.typography.bodySmall,
                        )

                        BudgetSummaryRow(
                            "Máximo desde esa fecha",
                            summary.maximoEnFechaBase,
                        )

                        BudgetSummaryRow(
                            "Máximo total durante el mes",
                            summary.maximoEnMes,
                        )

                        Text(
                            text = "El máximo mensual incluye lo disponible " +
                                    "desde la fecha de referencia; no se suman.",
                            style = MaterialTheme.typography.bodySmall,
                        )

                        BudgetSummaryRow(
                            "Ingresos pendientes",
                            summary.ingresosPendientes,
                        )

                        BudgetSummaryRow(
                            "Gastos pendientes",
                            summary.gastosPendientes,
                        )

                        BudgetSummaryRow(
                            "Saldo libre al cierre del mes",
                            summary.saldoLibreCierre,
                        )

                        if (summary.faltante != "0.00") {
                            Text(
                                text = "Faltan " +
                                        formatMxn(summary.faltante) +
                                        " para cubrir los gastos y la reserva " +
                                        "en las fechas indicadas. Ajusta el " +
                                        "presupuesto antes de distribuir " +
                                        "pagos de tarjetas.",
                                color = Prisma.colors.statusCritical,
                            )
                        }

                        Text(
                            text = "Los cobros futuros cuentan desde su fecha. " +
                                    "La proyección considera el cierre de cada día.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

            Spacer(Modifier.height(bottomNavBarSpace))
        }
    }

    if (state.pendingPeriod != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDiscard,
            title = {
                Text("¿Descartar cambios sin guardar?")
            },
            text = {
                Text(
                    "Se reemplazará este formulario por el presupuesto " +
                            "guardado del mes seleccionado.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDiscard,
                ) {
                    Text("Descartar y continuar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::cancelDiscard,
                ) {
                    Text("Seguir editando")
                }
            },
        )
    }

    state.editor?.let { editor ->
        Dialog(
            onDismissRequest = viewModel::closeMovement,
            properties = DialogProperties(
                usePlatformDefaultWidth = !isDesktopLayout,
            ),
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Prisma.colors.surface,
                contentColor = Prisma.colors.onSurface,
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = if (
                            editor.tipo == BudgetMovementType.INGRESO
                        ) {
                            "Ingreso pendiente"
                        } else {
                            "Gasto pendiente"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )

                    OutlinedTextField(
                        value = editor.concepto,
                        onValueChange = viewModel::onMovementName,
                        label = {
                            Text("Concepto")
                        },
                        enabled = editable,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    PrismaDateField(
                        value = editor.fecha,
                        onValueChange = viewModel::onMovementDate,
                        label = "Fecha prevista",
                        enabled = editable,
                        allowFuture = true,
                    )

                    BudgetAmountField(
                        label = "Monto (MXN)",
                        value = editor.monto,
                        onChange = viewModel::onMovementAmount,
                        enabled = editable,
                    )

                    editor.error?.let {
                        Text(
                            text = it,
                            color = Prisma.colors.statusCritical,
                        )
                    }

                    Button(
                        onClick = viewModel::saveMovement,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = editable,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrismaPalette.Dorado,
                            contentColor = PrismaPalette.Grafito,
                        ),
                    ) {
                        Text(
                            text = if (editor.id == null) {
                                "Agregar al presupuesto"
                            } else {
                                "Guardar movimiento"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    TextButton(
                        onClick = viewModel::closeMovement,
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetPanel(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )

            content()
        }
    }
}

@Composable
private fun BudgetAmountField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = {
            Text(label)
        },
        prefix = {
            Text("$ ")
        },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BudgetSummaryRow(
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
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = formatMxn(amount),
            fontWeight = FontWeight.SemiBold,
        )
    }
}