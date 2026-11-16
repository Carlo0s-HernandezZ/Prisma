package com.example.prisma.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prisma.api.IncomeFrequency
import com.example.prisma.api.IncomeType
import com.example.prisma.components.SoftCard
import com.example.prisma.income.IncomeViewModel

@Composable
fun IncomePlanningSection(viewModel: IncomeViewModel) {
    val incomes by viewModel.state.collectAsStateWithLifecycle()

    val controller = viewModel.planning

    val planning by controller.state.collectAsStateWithLifecycle()

    val availableIds = incomes.ingresos
        .map { it.id }
        .toSet()

    LaunchedEffect(
        incomes.isLoading,
        incomes.ingresos,
    ) {
        if (!incomes.isLoading) {
            controller.refresh()
        }
    }

    SoftCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Datos para tu análisis",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Prisma.colors.onSurface,
            )

            Text(
                text = "Confirma cuándo cobraste y si el ingreso " +
                        "se repite. Esto ayudará a personalizar " +
                        "tus planes de pago.",
                style = MaterialTheme.typography.bodyMedium,
                color = Prisma.colors.onSurface.copy(alpha = 0.7f),
            )

            val configured = planning.records.count {
                it.ingresoId in availableIds
            }

            Text(
                text = when {
                    planning.error != null ->
                        "Configuración sin actualizar."

                    planning.isLoading ||
                            !planning.hasLoaded ||
                            incomes.isLoading ->
                        "Cargando configuración..."

                    else ->
                        configured.toString() + " de " +
                                incomes.ingresos.size +
                                " ingresos configurados."
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            planning.error?.let { error ->
                Text(
                    text = error,
                    color = Prisma.colors.statusCritical,
                    style = MaterialTheme.typography.bodySmall,
                )

                TextButton(
                    onClick = controller::refresh,
                ) {
                    Text(
                        text = "Reintentar",
                        color = PrismaPalette.NaranjaAmbar,
                    )
                }
            }

            planning.message?.let { message ->
                Text(
                    text = message,
                    color = Prisma.colors.statusHealthy,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (
                !incomes.isLoading &&
                incomes.ingresos.isEmpty()
            ) {
                Text(
                    text = "Registra primero un ingreso.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = controller::openEditor,
                enabled = !incomes.isLoading &&
                        incomes.ingresos.isNotEmpty() &&
                        planning.hasLoaded &&
                        !planning.isLoading &&
                        planning.error == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrismaPalette.Dorado,
                    contentColor = PrismaPalette.Grafito,
                ),
            ) {
                Text(
                    text = "Configurar ingresos",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    val editor = planning.editor

    if (editor != null) {
        val selectedIncome = incomes.ingresos.firstOrNull {
            it.id == editor.incomeId
        }

        Dialog(
            onDismissRequest = controller::closeEditor,
            properties = DialogProperties(
                dismissOnClickOutside = !editor.isSaving,
                dismissOnBackPress = !editor.isSaving,
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Datos de cobro",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )

                    IncomePlanningSelector(
                        label = "Ingreso registrado",
                        value = editor.incomeId,
                        options = incomes.ingresos.map {
                            it.id to (
                                    it.nombre + " · " +
                                            formatMxn(it.montoNeto)
                                    )
                        },
                        enabled = !editor.isSaving &&
                                !planning.isLoading,
                        onSelect = controller::selectIncome,
                    )

                    if (
                        editor.incomeId != null &&
                        selectedIncome == null
                    ) {
                        Text(
                            text = "El ingreso seleccionado " +
                                    "ya no está disponible.",
                            color = Prisma.colors.statusCritical,
                        )
                    }

                    PrismaDateField(
                        value = editor.paymentDate,
                        onValueChange = controller::onDateChange,
                        label = "Fecha de cobro",
                        supportingText = "Fecha en la que recibiste este ingreso.",
                        enabled = !editor.isSaving && selectedIncome != null,
                        allowFuture = false,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    IncomePlanningSelector(
                        label = "Tipo de ingreso",
                        value = editor.type,
                        options = IncomeType.entries.map {
                            it to it.planningLabel()
                        },
                        enabled = !editor.isSaving &&
                                selectedIncome != null,
                        onSelect = controller::onTypeChange,
                    )

                    if (editor.type == IncomeType.RECURRENTE) {
                        IncomePlanningSelector(
                            label = "Frecuencia",
                            value = editor.frequency,
                            options = IncomeFrequency.entries.map {
                                it to it.planningLabel()
                            },
                            enabled = !editor.isSaving &&
                                    selectedIncome != null,
                            onSelect = controller::onFrequencyChange,
                        )
                    }

                    Text(
                        text = "Configurar este cobro no crea otro " +
                                "ingreso ni lo convierte en saldo " +
                                "disponible para tarjetas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Prisma.colors.onSurface.copy(
                            alpha = 0.7f,
                        ),
                    )

                    planning.error?.let { error ->
                        Text(
                            text = error,
                            color = Prisma.colors.statusCritical,
                        )

                        TextButton(
                            onClick = controller::refresh,
                            enabled = !editor.isSaving,
                        ) {
                            Text("Reintentar carga")
                        }
                    }

                    editor.error?.let { error ->
                        Text(
                            text = error,
                            color = Prisma.colors.statusCritical,
                        )
                    }

                    Button(
                        onClick = {
                            controller.save(availableIds)
                        },
                        enabled = !editor.isSaving &&
                                !planning.isLoading &&
                                planning.error == null &&
                                !incomes.isLoading &&
                                selectedIncome != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrismaPalette.Dorado,
                            contentColor = PrismaPalette.Grafito,
                        ),
                    ) {
                        if (editor.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = PrismaPalette.Grafito,
                            )
                        } else {
                            Text(
                                text = "Guardar datos de cobro",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    TextButton(
                        onClick = controller::closeEditor,
                        enabled = !editor.isSaving,
                    ) {
                        Text(
                            text = "Cerrar",
                            color = Prisma.colors.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Any> IncomePlanningSelector(
    label: String,
    value: T?,
    options: List<Pair<T, String>>,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (enabled) {
                expanded = !expanded
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = options.firstOrNull {
                it.first == value
            }?.second.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = {
                Text(label)
            },
            placeholder = {
                Text("Selecciona una opción")
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                )
            },
            modifier = Modifier
                .menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                )
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            },
        ) {
            options.forEach { (key, title) ->
                DropdownMenuItem(
                    text = {
                        Text(title)
                    },
                    onClick = {
                        if (enabled) {
                            onSelect(key)
                        }

                        expanded = false
                    },
                )
            }
        }
    }
}

private fun IncomeType.planningLabel(): String = when (this) {
    IncomeType.RECURRENTE ->
        "Recurrente"

    IncomeType.EXTRAORDINARIO ->
        "Extraordinario"
}

private fun IncomeFrequency.planningLabel(): String = when (this) {
    IncomeFrequency.SEMANAL ->
        "Semanal"

    IncomeFrequency.CATORCENAL ->
        "Cada 14 días"

    IncomeFrequency.QUINCENAL ->
        "Quincenal: dos cobros al mes"

    IncomeFrequency.MENSUAL ->
        "Mensual"

    IncomeFrequency.VARIABLE ->
        "Variable"
}