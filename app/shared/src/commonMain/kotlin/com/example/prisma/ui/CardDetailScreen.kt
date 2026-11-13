package com.example.prisma.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prisma.api.CardDocumentDto
import com.example.prisma.api.CardDto
import com.example.prisma.cards.CardDetailUiState
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

    if (isDesktopLayout) {
        // Web en pantalla ancha (Android e iOS nunca entran aquí).
        DesktopCardDetail(
            state = state,
            card = card,
            onBack = onBack,
            onUploadPdf = filePicker.launchPdfPicker,
            onUploadImage = filePicker.launchImagePicker,
            onDeleteDocument = viewModel::deleteDocument,
            modifier = modifier,
        )
        return
    }

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

// ---------------------------------------------------------------------------
// Diseño web (escritorio). Solo se usa cuando isDesktopLayout es true.
// ---------------------------------------------------------------------------

@Composable
private fun DesktopCardDetail(
    state: CardDetailUiState,
    card: CardDto,
    onBack: () -> Unit,
    onUploadPdf: () -> Unit,
    onUploadImage: () -> Unit,
    onDeleteDocument: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Prisma.colors
    Column(
        modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.screenContainer(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            // Encabezado: regresar + migas + nombre de la tarjeta
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.surface),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar a tarjetas", tint = colors.onSurface)
                }
                Column {
                    BrandLabel("Tarjetas · Detalle", color = colors.onSurface.copy(alpha = 0.6f))
                    Text(
                        card.alias,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
                        color = colors.onSurface,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                // Columna izquierda: tarjeta + subir archivos
                Column(Modifier.weight(0.38f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CardFace(card)
                    UploadPanel(isUploading = state.isUploading, onUploadPdf = onUploadPdf, onUploadImage = onUploadImage)
                    state.message?.let { msg ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background((if (state.isError) colors.statusCritical else PrismaPalette.NaranjaPrisma).copy(alpha = 0.12f))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                if (state.isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = if (state.isError) colors.statusCritical else PrismaPalette.NaranjaAmbar,
                            )
                            Text(msg, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                        }
                    }
                }

                // Columna derecha: estados de cuenta extraídos
                Column(Modifier.weight(0.62f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Estados de cuenta extraídos",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                            color = colors.onSurface,
                        )
                        if (state.documents.isNotEmpty()) {
                            Text(
                                "${state.documents.size}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = PrismaPalette.Grafito,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(PrismaPalette.Dorado)
                                    .padding(horizontal = 10.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        "Extraemos automáticamente la información financiera de tus PDF o imágenes, sin guardar datos personales.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface.copy(alpha = 0.6f),
                    )

                    when {
                        state.isLoading && state.documents.isEmpty() -> SoftCard(Modifier.fillMaxWidth()) {
                            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = PrismaPalette.NaranjaPrisma)
                            }
                        }
                        state.documents.isEmpty() -> SoftCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = PrismaPalette.NaranjaPrisma.copy(alpha = 0.6f),
                                    modifier = Modifier.size(44.dp),
                                )
                                Text(
                                    "Aún no hay estados de cuenta",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onSurface,
                                )
                                Text(
                                    "Sube un PDF o una imagen desde el panel de la izquierda.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Cuadrícula de 2 columnas
                            state.documents.chunked(2).forEach { row ->
                                Row(
                                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    row.forEach { doc ->
                                        Box(Modifier.weight(1f).fillMaxHeight()) {
                                            ExtractedStatementCard(document = doc, onDelete = { onDeleteDocument(doc.id) })
                                        }
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Tarjeta con aspecto de plástico: fondo Grafito, acentos naranja y texto Crema. */
@Composable
private fun CardFace(card: CardDto) {
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f) // proporción de una tarjeta bancaria (ISO/IEC 7810)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(listOf(PrismaPalette.Grafito, PrismaPalette.Grafito, PrismaPalette.NaranjaAmbar.copy(alpha = 0.85f))),
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                card.banco,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
                color = PrismaPalette.Crema,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Filled.CreditCard, contentDescription = null, tint = PrismaPalette.Dorado, modifier = Modifier.size(28.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "•••• •••• •••• ${card.ultimosCuatro?.takeIf { it.isNotBlank() } ?: "••••"}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 2.sp),
                color = PrismaPalette.Crema,
            )
            BrandLabel(card.alias, color = PrismaPalette.Dorado)
        }
    }
}

/** Zona para subir documentos con borde punteado y botones directos (sin diálogo intermedio). */
@Composable
private fun UploadPanel(isUploading: Boolean, onUploadPdf: () -> Unit, onUploadImage: () -> Unit) {
    val colors = Prisma.colors
    val dashColor = PrismaPalette.NaranjaPrisma.copy(alpha = 0.5f)
    SoftCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .drawBehind {
                    drawRoundRect(
                        color = dashColor,
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                    )
                }
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isUploading) {
                CircularProgressIndicator(color = PrismaPalette.NaranjaPrisma)
                Text(
                    "Extrayendo información del archivo…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
            } else {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = PrismaPalette.NaranjaPrisma, modifier = Modifier.size(40.dp))
                Text(
                    "Sube tu estado de cuenta",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                )
                Text(
                    "PDF o imagen (JPG, PNG). Puedes elegir varias imágenes a la vez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onUploadPdf,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = PrismaPalette.Dorado, contentColor = PrismaPalette.Grafito),
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Subir PDF", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onUploadImage,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, PrismaPalette.NaranjaPrisma),
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = PrismaPalette.NaranjaAmbar, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Subir imagen", color = colors.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
