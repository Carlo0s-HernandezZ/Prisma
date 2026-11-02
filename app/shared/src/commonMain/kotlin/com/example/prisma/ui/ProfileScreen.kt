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
import com.example.prisma.components.BottomNavBar
import com.example.prisma.components.NavDestination
import com.example.prisma.components.SoftCard
import com.example.prisma.defaultBaseUrl
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class PersonalDataDto(
    val email: String,
    val nombre: String,
    val apellido: String,
    val fechaNacimiento: String? = null
)

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
    onNavigate: (NavDestination) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val client = remember {
        HttpClient {
            install(ContentNegotiation) { json() }
        }
    }
    val baseUrl = remember { defaultBaseUrl() }

    var nombre by remember { mutableStateOf("") }
    var apellido by remember { mutableStateOf("") }
    var fechaNacimiento by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            fechaNacimiento = formatEpochMillisToDateString(millis)
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

    // Fetch existing profile on launch
    LaunchedEffect(userEmail) {
        if (userEmail.isNotBlank()) {
            try {
                val response = client.get("$baseUrl/api/v1/user/profile?email=$userEmail")
                if (response.status.isSuccess()) {
                    val dto: PersonalDataDto = response.body()
                    nombre = dto.nombre
                    apellido = dto.apellido
                    fechaNacimiento = dto.fechaNacimiento ?: ""
                }
            } catch (_: Exception) {}
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
                        text = userEmail.ifBlank { "usuario@prisma.com" },
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
                            value = nombre,
                            onValueChange = { nombre = it },
                            label = { Text("Nombre") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = apellido,
                            onValueChange = { apellido = it },
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
                                value = fechaNacimiento,
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

                        message?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isError) Prisma.colors.statusCritical else Prisma.colors.statusHealthy,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )
                        }

                        // Save / Update Button
                        Button(
                            onClick = {
                                if (nombre.isBlank() || apellido.isBlank()) {
                                    message = "Por favor ingresa nombre y apellido."
                                    isError = true
                                    return@Button
                                }
                                isLoading = true
                                message = null
                                scope.launch {
                                    try {
                                        val response = client.post("$baseUrl/api/v1/user/profile") {
                                            contentType(ContentType.Application.Json)
                                            setBody(
                                                PersonalDataDto(
                                                    email = userEmail.ifBlank { "usuario@prisma.com" },
                                                    nombre = nombre,
                                                    apellido = apellido,
                                                    fechaNacimiento = fechaNacimiento.ifBlank { null }
                                                )
                                            )
                                        }
                                        if (response.status.isSuccess()) {
                                            message = "¡Datos personales guardados con éxito!"
                                            isError = false
                                        } else {
                                            message = "Error al guardar los datos."
                                            isError = true
                                        }
                                    } catch (e: Exception) {
                                        message = "Error de red: ${e.message}"
                                        isError = true
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = PrismaPalette.Dorado)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Guardar / Actualizar", color = PrismaPalette.Grafito, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Delete Personal Data Button
                        OutlinedButton(
                            onClick = {
                                isLoading = true
                                message = null
                                scope.launch {
                                    try {
                                        val emailToDelete = userEmail.ifBlank { "usuario@prisma.com" }
                                        val response = client.delete("$baseUrl/api/v1/user/profile?email=$emailToDelete")
                                        if (response.status.isSuccess()) {
                                            nombre = ""
                                            apellido = ""
                                            fechaNacimiento = ""
                                            message = "Datos personales eliminados de PostgreSQL."
                                            isError = false
                                        } else {
                                            message = "No se encontraron datos para eliminar."
                                            isError = true
                                        }
                                    } catch (_: Exception) {
                                        message = "Error de red al eliminar."
                                        isError = true
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
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
            }

            // Bottom Nav Bar
            BottomNavBar(selected = NavDestination.Profile, onSelect = onNavigate)
        }
    }
}
