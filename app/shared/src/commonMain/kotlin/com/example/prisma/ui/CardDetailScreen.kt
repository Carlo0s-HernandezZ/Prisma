package com.example.prisma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prisma.api.CardDocumentDto
import com.example.prisma.cards.CardDetailViewModel
import com.example.prisma.components.SoftCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    viewModel: CardDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val card = state.card ?: return

    var showTypeSelector by remember { mutableStateOf(false) }

    val filePicker = rememberFilePicker(
        onFileSelected = { name, mime, bytes ->
            viewModel.uploadDocument(name, mime, bytes)
        }
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(card.alias, fontWeight = FontWeight.SemiBold, color = Prisma.colors.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Prisma.colors.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { showTypeSelector = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Subir estado de cuenta", tint = PrismaPalette.NaranjaPrisma)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Prisma.colors.background),
            )
        },
        containerColor = Prisma.colors.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Tarjeta Info Header
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PrismaPalette.NaranjaPrisma.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.CreditCard,
                                    contentDescription = null,
                                    tint = PrismaPalette.NaranjaPrisma,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Column {
                                Text(
                                    card.banco,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Prisma.colors.onSurface,
                                )
                                Text(
                                    card.alias,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Prisma.colors.onSurface.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }

                    if (!card.ultimosCuatro.isNullOrBlank()) {
                        HorizontalDivider(color = Prisma.colors.onSurface.copy(alpha = 0.1f))
                        Text(
                            "Terminación: •••• ${card.ultimosCuatro}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Prisma.colors.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            // Sección: Estados de cuenta y Comprobantes
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Estados de Cuenta Extraídos (PDF / Imagen)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Prisma.colors.onSurface,
                )
                Text(
                    "Al seleccionar tu PDF o imagen, el sistema extrae automáticamente la información financiera omitiendo datos personales.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Prisma.colors.onSurface.copy(alpha = 0.6f),
                )

                if (state.isLoading || state.isUploading) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(color = PrismaPalette.NaranjaPrisma)
                            Text(
                                if (state.isUploading) "Abriendo y extrayendo información del archivo..." else "Cargando documentos...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Prisma.colors.onSurface.copy(alpha = 0.8f),
                            )
                        }
                    }
                } else if (state.documents.isEmpty()) {
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "No hay estados de cuenta procesados. Presiona '+' arriba para seleccionar un archivo.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Prisma.colors.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.documents.forEach { doc ->
                            ExtractedStatementCard(
                                document = doc,
                                onDelete = { viewModel.deleteDocument(doc.id) },
                            )
                        }
                    }
                }
            }

            state.message?.let { msg ->
                Text(
                    msg,
                    color = if (state.isError) MaterialTheme.colorScheme.error else PrismaPalette.NaranjaPrisma,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                )
            }
        }
    }

    // Selector nativo (PDF o Imagen desde el dispositivo)
    if (showTypeSelector) {
        AlertDialog(
            onDismissRequest = { showTypeSelector = false },
            containerColor = Prisma.colors.surface,
            titleContentColor = Prisma.colors.onSurface,
            textContentColor = Prisma.colors.onSurface.copy(alpha = 0.8f),
            title = { Text("Seleccionar documento") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Elige de dónde deseas cargar tu estado de cuenta o comprobante:")
                    Button(
                        onClick = {
                            showTypeSelector = false
                            filePicker.launchPdfPicker()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrismaPalette.Dorado, contentColor = PrismaPalette.Grafito),
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Buscar PDF en el dispositivo", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            showTypeSelector = false
                            filePicker.launchImagePicker()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrismaPalette.Dorado, contentColor = PrismaPalette.Grafito),
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir Galería / Imagen", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTypeSelector = false }) {
                    Text("Cancelar", color = Prisma.colors.onSurface)
                }
            },
        )
    }
}

@Composable
private fun ExtractedStatementCard(
    document: CardDocumentDto,
    onDelete: () -> Unit,
) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (document.tipoMime.contains("pdf")) Icons.Filled.Description else Icons.Filled.Image,
                        contentDescription = null,
                        tint = PrismaPalette.NaranjaPrisma,
                        modifier = Modifier.size(24.dp),
                    )
                    Column {
                        Text(
                            document.nombreArchivo,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Prisma.colors.onSurface,
                        )
                        if (!document.periodo.isNullOrBlank() || !document.fechaCorte.isNullOrBlank()) {
                            Text(
                                buildString {
                                    if (!document.periodo.isNullOrBlank()) append("Periodo: ${document.periodo}")
                                    if (!document.periodo.isNullOrBlank() && !document.fechaCorte.isNullOrBlank()) append(" • ")
                                    if (!document.fechaCorte.isNullOrBlank()) append("Corte: ${document.fechaCorte}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Prisma.colors.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    )
                }
            }

            HorizontalDivider(color = Prisma.colors.onSurface.copy(alpha = 0.1f))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "📊 Información Financiera Extraída:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = PrismaPalette.Dorado,
                )
                
                val monto = document.montoPesos
                if (!monto.isNullOrBlank()) {
                    StatementRowItem("Monto Total Extraído", "$monto MXN")
                }
                val corte = document.fechaCorte
                if (!corte.isNullOrBlank()) {
                    StatementRowItem("Fecha de Corte", corte)
                }
                val pago = document.fechaPago
                if (!pago.isNullOrBlank()) {
                    StatementRowItem("Fecha Límite de Pago", pago)
                }
            }
        }
    }
}

@Composable
private fun StatementRowItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Prisma.colors.onSurface.copy(alpha = 0.7f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = Prisma.colors.onSurface,
        )
    }
}
