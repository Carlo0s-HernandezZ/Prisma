package com.example.prisma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prisma.components.BottomNavBarReservedSpace
import com.example.prisma.components.SoftCard
import com.example.prisma.profile.ProfileViewModel

@Composable
fun ProfileScreen(
    userEmail: String,
    viewModel: ProfileViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    biometric: BiometricSetting? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = {
                Text("¿Eliminar datos personales?")
            },
            text = {
                Text(
                    "Se borrarán tu nombre, apellido y fecha de nacimiento. " +
                            "Esta acción no se puede deshacer.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDelete,
                ) {
                    Text(
                        text = "Eliminar",
                        color = Prisma.colors.statusCritical,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::cancelDelete,
                ) {
                    Text(
                        text = "Cancelar",
                        color = Prisma.colors.onSurface,
                    )
                }
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Prisma.colors.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Perfil de Usuario",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Prisma.colors.onSurface,
                    )

                    Text(
                        text = userEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Prisma.colors.onSurface.copy(
                            alpha = 0.7f,
                        ),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SoftCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Datos Personales",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )

                        OutlinedTextField(
                            value = state.nombre,
                            onValueChange = viewModel::onNombreChange,
                            enabled = !state.isLoading,
                            label = {
                                Text("Nombre")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        OutlinedTextField(
                            value = state.apellido,
                            onValueChange = viewModel::onApellidoChange,
                            enabled = !state.isLoading,
                            label = {
                                Text("Apellido")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        PrismaDateField(
                            value = state.fechaNacimiento,
                            onValueChange = viewModel::onFechaChange,
                            label = "Fecha de Nacimiento",
                            enabled = !state.isLoading,
                            allowFuture = false,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        state.message?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (state.isError) {
                                    Prisma.colors.statusCritical
                                } else {
                                    Prisma.colors.statusHealthy
                                },
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            )
                        }

                        Button(
                            onClick = viewModel::save,
                            enabled = !state.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrismaPalette.Dorado,
                            ),
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    text = "Guardar / Actualizar",
                                    color = PrismaPalette.Grafito,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = viewModel::requestDelete,
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Prisma.colors.statusCritical,
                            ),
                        ) {
                            Text("Eliminar Datos Personales")
                        }
                    }
                }

                biometric?.let { setting ->
                    BiometricSettingCard(setting)
                }

                SoftCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BrandLabel(
                            text = "Legal y privacidad",
                            color = Prisma.colors.onSurface.copy(
                                alpha = 0.6f,
                            ),
                        )

                        LegalLinks(
                            color = PrismaPalette.NaranjaAmbar,
                            fontSize = 14.sp,
                        )
                    }
                }

                SoftCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onLogout)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Cerrar Sesión",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Prisma.colors.statusCritical,
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(
                        BottomNavBarReservedSpace,
                    ),
                )
            }
        }
    }
}

/**
 * Estado del interruptor de acceso biométrico.
 */
data class BiometricSetting(
    val label: String,
    val isFace: Boolean,
    val enabled: Boolean,
    val busy: Boolean,
    val onToggle: (Boolean) -> Unit,
)

@Composable
private fun BiometricSettingCard(
    setting: BiometricSetting,
) {
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (setting.isFace) {
                    Icons.Filled.Face
                } else {
                    Icons.Filled.Fingerprint
                },
                contentDescription = null,
                tint = PrismaPalette.NaranjaPrisma,
                modifier = Modifier.size(28.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Entrar con ${setting.label}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Prisma.colors.onSurface,
                )

                Text(
                    text = "Usa tu huella o Face ID para entrar al instante",
                    style = MaterialTheme.typography.bodySmall,
                    color = Prisma.colors.onSurface.copy(
                        alpha = 0.7f,
                    ),
                )
            }

            if (setting.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = PrismaPalette.NaranjaPrisma,
                )
            } else {
                Switch(
                    checked = setting.enabled,
                    onCheckedChange = setting.onToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = PrismaPalette.NaranjaPrisma,
                    ),
                )
            }
        }
    }
}