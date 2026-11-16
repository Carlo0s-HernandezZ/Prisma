@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    kotlin.time.ExperimentalTime::class,
)

package com.example.prisma.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private val calendarZone = TimeZone.of("America/Mexico_City")

private val calendarMonths = listOf(
    "Enero",
    "Febrero",
    "Marzo",
    "Abril",
    "Mayo",
    "Junio",
    "Julio",
    "Agosto",
    "Septiembre",
    "Octubre",
    "Noviembre",
    "Diciembre",
)

private fun calendarToday(): LocalDate =
    Clock.System.now()
        .toLocalDateTime(calendarZone)
        .date

private fun calendarDate(value: String): LocalDate? =
    runCatching {
        LocalDate.parse(value)
    }.getOrNull()?.takeIf {
        it.year in 1..9999
    }

/**
 * Material DatePicker representa la fecha seleccionada
 * mediante la medianoche UTC.
 */
private fun calendarMillis(date: LocalDate): Long =
    date.atStartOfDayIn(TimeZone.UTC)
        .toEpochMilliseconds()

private fun calendarDateFromMillis(millis: Long): LocalDate =
    Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.UTC)
        .date

/**
 * Muestra DD/MM/AAAA.
 * Entrega AAAA-MM-DD a onValueChange.
 */
@Composable
fun PrismaDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    allowFuture: Boolean = true,
    supportingText: String? = null,
) {
    var showCalendar by rememberSaveable {
        mutableStateOf(false)
    }

    val savedDate = calendarDate(value)

    val displayValue = savedDate
        ?.toString()
        ?.let(::formatIsoDateTime)
        .orEmpty()

    CalendarPickerField(
        value = displayValue,
        label = label,
        modifier = modifier,
        enabled = enabled,
        supportingText = supportingText,
        onClick = {
            showCalendar = true
        },
    )

    if (showCalendar && enabled) {
        val today = calendarToday()

        val selectableDates = remember(today, allowFuture) {
            object : SelectableDates {
                override fun isSelectableDate(
                    utcTimeMillis: Long,
                ): Boolean {
                    val date = calendarDateFromMillis(
                        utcTimeMillis,
                    )

                    return date.year in 1..9999 &&
                            (allowFuture || date <= today)
                }

                override fun isSelectableYear(
                    year: Int,
                ): Boolean {
                    return year in 1..9999 &&
                            (allowFuture || year <= today.year)
                }
            }
        }

        val firstYear = minOf(
            1900,
            savedDate?.year ?: 1900,
        )

        val lastYear = maxOf(
            2100,
            today.year,
            savedDate?.year ?: 2100,
        )

        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis =
                savedDate?.let(::calendarMillis),
            initialDisplayedMonthMillis =
                calendarMillis(savedDate ?: today),
            yearRange = firstYear..lastYear,
            selectableDates = selectableDates,
        )

        val chosenDate = pickerState.selectedDateMillis
            ?.let(::calendarDateFromMillis)

        val canConfirm = chosenDate != null &&
                (allowFuture || chosenDate <= today)

        DatePickerDialog(
            onDismissRequest = {
                showCalendar = false
            },
            confirmButton = {
                TextButton(
                    enabled = canConfirm,
                    onClick = {
                        if (chosenDate != null && canConfirm) {
                            onValueChange(
                                chosenDate.toString(),
                            )

                            showCalendar = false
                        }
                    },
                ) {
                    Text("Seleccionar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCalendar = false
                    },
                ) {
                    Text("Cancelar")
                }
            },
        ) {
            DatePicker(
                state = pickerState,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = Prisma.colors.surface,
                    selectedDayContainerColor =
                        PrismaPalette.Dorado,
                    selectedDayContentColor =
                        PrismaPalette.Grafito,
                    todayContentColor =
                        PrismaPalette.NaranjaAmbar,
                    todayDateBorderColor =
                        PrismaPalette.NaranjaAmbar,
                ),
            )
        }
    }
}

/**
 * Muestra el nombre del mes y el año.
 * Entrega AAAA-MM a onValueChange.
 */
@Composable
fun PrismaMonthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var showCalendar by rememberSaveable {
        mutableStateOf(false)
    }

    val savedDate = if (
        Regex("[0-9]{4}-[0-9]{2}").matches(value)
    ) {
        calendarDate(value + "-01")
    } else {
        null
    }

    val savedMonth = savedDate
        ?.month
        ?.ordinal
        ?.plus(1)

    val displayValue = if (
        savedDate != null &&
        savedMonth != null
    ) {
        calendarMonths[savedMonth - 1] +
                " " + savedDate.year
    } else {
        ""
    }

    CalendarPickerField(
        value = displayValue,
        label = label,
        modifier = modifier,
        enabled = enabled,
        supportingText = null,
        onClick = {
            showCalendar = true
        },
    )

    if (showCalendar && enabled) {
        var year by rememberSaveable {
            mutableStateOf(
                savedDate?.year ?: calendarToday().year,
            )
        }

        var selectedMonth by rememberSaveable {
            mutableStateOf<Int?>(savedMonth)
        }

        AlertDialog(
            onDismissRequest = {
                showCalendar = false
            },
            containerColor = Prisma.colors.surface,
            title = {
                Text("Seleccionar periodo")
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        IconButton(
                            enabled = year > 1,
                            onClick = {
                                year -= 1
                                selectedMonth = null
                            },
                        ) {
                            Icon(
                                imageVector =
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription =
                                    "Año anterior",
                            )
                        }

                        Text(
                            text = year.toString(),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                        )

                        IconButton(
                            enabled = year < 9999,
                            onClick = {
                                year += 1
                                selectedMonth = null
                            },
                        ) {
                            Icon(
                                imageVector =
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription =
                                    "Año siguiente",
                            )
                        }
                    }

                    (1..12).chunked(3).forEach { row ->
                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { month ->
                                val selected =
                                    selectedMonth == month

                                OutlinedButton(
                                    onClick = {
                                        selectedMonth = month
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(
                                        12.dp,
                                    ),
                                    contentPadding =
                                        PaddingValues(0.dp),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (selected) {
                                            PrismaPalette.Dorado
                                        } else {
                                            Prisma.colors.onSurface
                                                .copy(alpha = 0.25f)
                                        },
                                    ),
                                    colors =
                                        ButtonDefaults.outlinedButtonColors(
                                            containerColor =
                                                if (selected) {
                                                    PrismaPalette.Dorado
                                                } else {
                                                    Color.Transparent
                                                },
                                            contentColor =
                                                if (selected) {
                                                    PrismaPalette.Grafito
                                                } else {
                                                    Prisma.colors.onSurface
                                                },
                                        ),
                                ) {
                                    Text(
                                        calendarMonths[month - 1]
                                            .take(3),
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedMonth != null,
                    onClick = {
                        selectedMonth?.let { month ->
                            val selectedPeriod =
                                year.toString()
                                    .padStart(4, '0') +
                                        "-" +
                                        month.toString()
                                            .padStart(2, '0')

                            onValueChange(selectedPeriod)
                            showCalendar = false
                        }
                    },
                ) {
                    Text("Seleccionar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCalendar = false
                    },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun CalendarPickerField(
    value: String,
    label: String,
    modifier: Modifier,
    enabled: Boolean,
    supportingText: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = {
                Text(label)
            },
            placeholder = {
                Text("Toca para seleccionar")
            },
            supportingText = supportingText?.let { message ->
                {
                    Text(message)
                }
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = null,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties {
                    canFocus = false
                }
                .clearAndSetSemantics {},
        )

        // Toda el área del campo abre el selector.
        Box(
            modifier = Modifier
                .matchParentSize()
                .semantics {
                    contentDescription = label +
                            (
                                    supportingText?.let {
                                        ". " + it
                                    } ?: ""
                                    )

                    stateDescription = value.ifBlank {
                        "Sin seleccionar"
                    }
                }
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
        )
    }
}