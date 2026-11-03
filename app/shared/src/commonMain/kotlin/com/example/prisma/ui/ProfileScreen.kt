package com.example.prisma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prisma.components.BottomNavBarReservedSpace
import com.example.prisma.components.SoftCard
import com.example.prisma.profile.ProfileViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private fun formatEpochMillisToDateString(millis: Long): String {
    val totalSeconds = millis / 1000
    val days = (totalSeconds / 86400).toInt()
    var L = days + 68569 + 2440588
    val n = (4 * L) / 146097
    L = L - (146097 * n + 3) / 4
    val i = (4000 * (L + 1)) / 1464691
    L = L - (1461 * i) / 4 + 31
    val j = (80 * L) / 2447
    val day = L - (2447 * j) / 80
    L = j / 11
    val month = j + 2 - 12 * L
    val year = 100 * (n - 49) + i + L

    val y = year.toString().padStart(4, '0')
    val m = month.toString().padStart(2, '0')
    val d = day.toString().padStart(2, '0')
    return "$y-$m-$d"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userEmail: String,
    viewModel: ProfileViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("¿Eliminar datos personales?") },
            text = { Text("Se borrarán tu nombre, apellido y fecha de nacimiento. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Eliminar", color = Prisma.colors.statusCritical)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) {
                    Text("Cancelar", color = Prisma.colors.onSurface)
                }
            }
        )
    }

    // Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.onFechaChange(formatEpochMillisToDateString(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", color = Prisma.colors.onSurface)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar", color = Prisma.colors.onSurface)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(Prisma.colors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            SoftCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Perfil de Usuario",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Prisma.colors.onSurface
                    )
                    Text(
                        text = userEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Prisma.colors.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            // Scrollable Form
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Datos Personales",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        OutlinedTextField(
                            value = state.nombre,
                            onValueChange = viewModel::onNombreChange,
                            enabled = !state.isLoading,
                            label = { Text("Nombre") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = state.apellido,
                            onValueChange = viewModel::onApellidoChange,
                            enabled = !state.isLoading,
                            label = { Text("Apellido") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Interactive Date Selection Field
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDatePicker = true }
                        ) {
                            OutlinedTextField(
                                value = state.fechaNacimiento,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Fecha de Nacimiento") },
                                placeholder = { Text("Toca para seleccionar fecha") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.DateRange,
                                        contentDescription = "Seleccionar fecha",
                                        modifier = Modifier.clickable { showDatePicker = true }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        state.message?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (state.isError) Prisma.colors.statusCritical else Prisma.colors.statusHealthy,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )
                        }

                        // Save / Update Button
                        Button(
                            onClick = viewModel::save,
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = PrismaPalette.Dorado)
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Guardar / Actualizar", color = PrismaPalette.Grafito, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Delete Personal Data Button (pide confirmación)
                        OutlinedButton(
                            onClick = viewModel::requestDelete,
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Prisma.colors.statusCritical)
                        ) {
                            Text("Eliminar Datos Personales")
                        }
                    }
                }

                // Logout Button
                SoftCard(Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onLogout)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cerrar Sesión",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Prisma.colors.statusCritical
                        )
                    }
                }

                Spacer(Modifier.height(BottomNavBarReservedSpace))
            }
        }
    }
}
