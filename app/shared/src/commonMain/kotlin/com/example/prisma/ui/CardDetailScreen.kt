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
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.example.prisma.api.CardDocumentDto
import com.example.prisma.api.CardDto
import com.example.prisma.cards.AutomaticStatementViewModel
import com.example.prisma.cards.CardDetailUiState
import com.example.prisma.cards.CardDetailViewModel
import com.example.prisma.cards.KtorAutomaticStatementRepository
import com.example.prisma.components.SoftCard
import com.example.prisma.components.bottomNavBarSpace

/**
 * Pantalla de detalle de una tarjeta.
 *
 * Responsabilidades:
 * - Mostrar los datos de identificación de la tarjeta.
 * - Seleccionar un PDF o las imágenes de un mismo estado de cuenta.
 * - Mostrar el resultado de la incorporación automática.
 * - Permitir abrir los planes de pago en Análisis.
 * - Mantener la consulta y eliminación del historial anterior.
 *
 * La extracción y los cálculos financieros se realizan fuera de esta pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    viewModel: CardDetailViewModel,
    onBack: () -> Unit,
    token: String,
    onSessionExpired: () -> Unit,
    onViewPlans: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Estado general de la tarjeta y de sus documentos anteriores.
    val state by viewModel.state.collectAsStateWithLifecycle()

    // No se dibuja el detalle si todavía no existe una tarjeta seleccionada.
    val card = state.card ?: return

    /*
     * Cada tarjeta y cada sesión tienen su propio lector.
     *
     * AutomaticStatementViewModel recupera las lecturas guardadas
     * cuando se crea; no es necesario volver a seleccionar el PDF.
     */
    val reader = composeViewModel(
        key = "statement-auto-${card.id}-$token",
    ) {
        AutomaticStatementViewModel(
            cardId = card.id,
            repository = KtorAutomaticStatementRepository(token),
        )
    }

    val readerState by reader.state.collectAsStateWithLifecycle()

    // Evita iniciar otra selección durante una carga, lectura o aclaración.
    val readerBusy =
        readerState.isLoading ||
                readerState.isReading ||
                readerState.isResolving

    // Mantiene disponible la versión más reciente del callback.
    val currentOnSessionExpired by rememberUpdatedState(
        onSessionExpired,
    )

    // Tanto el historial como el lector pueden detectar una sesión vencida.
    LaunchedEffect(
        state.sessionExpired,
        readerState.sessionExpired,
    ) {
        if (state.sessionExpired || readerState.sessionExpired) {
            currentOnSessionExpired()
        }
    }

    // Estado local del diálogo para elegir PDF o imágenes.
    var showTypeSelector by remember(card.id) {
        mutableStateOf(false)
    }

    /*
     * Este selector entrega todos los archivos seleccionados en un solo lote.
     *
     * El lector inicia la importación automáticamente.
     * Aquí no se llama a uploadDocument, que pertenece al historial anterior.
     */
    val filePicker = rememberStatementPicker(
        onFilesSelected = reader::importFiles,
        onError = reader::fileError,
    )

    // En escritorio se conserva la distribución de dos columnas.
    if (isDesktopLayout) {
        DesktopCardDetail(
            state = state,
            card = card,
            onBack = onBack,
            onUploadPdf = filePicker.launchPdfPicker,
            onUploadImage = filePicker.launchImagePicker,
            onDeleteDocument = viewModel::deleteDocument,
            isReading = readerBusy,
            modifier = modifier,
            readerContent = {
                // Se utiliza la misma sección automática que en móvil.
                AutomaticStatementSection(
                    viewModel = reader,
                    onViewPlans = onViewPlans,
                )
            },
        )

        return
    }

    // ---------------------------------------------------------------
    // Vista móvil
    // ---------------------------------------------------------------
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = card.alias,
                        fontWeight = FontWeight.SemiBold,
                        color = Prisma.colors.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                            tint = Prisma.colors.onSurface,
                        )
                    }
                },
                actions = {
                    // Abre el diálogo de selección del estado de cuenta.
                    IconButton(
                        onClick = {
                            showTypeSelector = true
                        },
                        enabled = !readerBusy,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Subir estado de cuenta",
                            tint = PrismaPalette.NaranjaPrisma,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Prisma.colors.background,
                ),
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
            // Datos de identificación de la tarjeta.
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        PrismaPalette.NaranjaPrisma.copy(
                                            alpha = 0.15f,
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CreditCard,
                                    contentDescription = null,
                                    tint = PrismaPalette.NaranjaPrisma,
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            Column {
                                Text(
                                    text = card.banco,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = Prisma.colors.onSurface,
                                )

                                Text(
                                    text = card.alias,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Prisma.colors.onSurface.copy(
                                        alpha = 0.7f,
                                    ),
                                )
                            }
                        }
                    }

                    if (!card.ultimosCuatro.isNullOrBlank()) {
                        HorizontalDivider(
                            color = Prisma.colors.onSurface.copy(
                                alpha = 0.1f,
                            ),
                        )

                        Text(
                            text = "Terminación: •••• ${card.ultimosCuatro}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                            color = Prisma.colors.onSurface.copy(
                                alpha = 0.6f,
                            ),
                        )
                    }
                }
            }

            /*
             * Resultado del flujo automático.
             *
             * Sustituye las llamadas anteriores a:
             * - StatementReaderSection
             * - StatementConfirmationSection
             *
             * Las aclaraciones excepcionales se muestran dentro de esta sección.
             */
            AutomaticStatementSection(
                viewModel = reader,
                onViewPlans = onViewPlans,
            )

            /*
             * Historial de documentos del flujo anterior.
             *
             * Las lecturas automáticas guardadas se consultan en la sección
             * superior, mediante su opción de estados guardados.
             */
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Historial de documentos (PDF / Imagen)",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Prisma.colors.onSurface,
                )

                Text(
                    text = "Archivos guardados anteriormente para esta tarjeta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Prisma.colors.onSurface.copy(
                        alpha = 0.6f,
                    ),
                )

                when {
                    // Indicador del repositorio del historial.
                    state.isLoading || state.isUploading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    color = PrismaPalette.NaranjaPrisma,
                                )

                                Text(
                                    text = if (state.isUploading) {
                                        "Agregando documento al historial..."
                                    } else {
                                        "Cargando documentos..."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Prisma.colors.onSurface.copy(
                                        alpha = 0.8f,
                                    ),
                                )
                            }
                        }
                    }

                    state.documents.isEmpty() -> {
                        SoftCard(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No hay archivos en este historial. " +
                                            "Los estados incorporados se consultan arriba.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Prisma.colors.onSurface.copy(
                                        alpha = 0.6f,
                                    ),
                                )
                            }
                        }
                    }

                    else -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            state.documents.forEach { document ->
                                ExtractedStatementCard(
                                    document = document,
                                    onDelete = {
                                        viewModel.deleteDocument(
                                            document.id,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Mensajes de operaciones del historial, como eliminar un archivo.
            state.message?.let { message ->
                Text(
                    text = message,
                    color = if (state.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        PrismaPalette.NaranjaPrisma
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }

            // Impide que la navegación flotante cubra el contenido final.
            Spacer(
                modifier = Modifier.height(bottomNavBarSpace),
            )
        }
    }

    // ---------------------------------------------------------------
    // Diálogo para seleccionar el tipo de archivo
    // ---------------------------------------------------------------
    if (showTypeSelector) {
        AlertDialog(
            onDismissRequest = {
                showTypeSelector = false
            },
            containerColor = Prisma.colors.surface,
            titleContentColor = Prisma.colors.onSurface,
            textContentColor = Prisma.colors.onSurface.copy(
                alpha = 0.8f,
            ),
            title = {
                Text(
                    text = "Seleccionar documento",
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Selecciona un PDF o las imágenes del mismo estado. " +
                                "El análisis comenzará automáticamente.",
                    )

                    Button(
                        onClick = {
                            // Cierra el diálogo antes de abrir el selector.
                            showTypeSelector = false
                            filePicker.launchPdfPicker()
                        },
                        enabled = !readerBusy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrismaPalette.Dorado,
                            contentColor = PrismaPalette.Grafito,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                        )

                        Spacer(
                            modifier = Modifier.width(8.dp),
                        )

                        Text(
                            text = "Buscar PDF en el dispositivo",
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Button(
                        onClick = {
                            showTypeSelector = false
                            filePicker.launchImagePicker()
                        },
                        enabled = !readerBusy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrismaPalette.Dorado,
                            contentColor = PrismaPalette.Grafito,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = null,
                        )

                        Spacer(
                            modifier = Modifier.width(8.dp),
                        )

                        Text(
                            text = "Seleccionar imágenes",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showTypeSelector = false
                    },
                ) {
                    Text(
                        text = "Cancelar",
                        color = Prisma.colors.onSurface,
                    )
                }
            },
        )
    }
}

/**
 * Representa un archivo del historial anterior.
 *
 * Muestra sus datos básicos y permite eliminarlo.
 * Los importes financieros se consultan en el resumen automático.
 */
@Composable
private fun ExtractedStatementCard(
    document: CardDocumentDto,
    onDelete: () -> Unit,
) {
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (
                        document.tipoMime.contains(
                            "pdf",
                            ignoreCase = true,
                        )
                    ) {
                        Icons.Filled.Description
                    } else {
                        Icons.Filled.Image
                    },
                    contentDescription = null,
                    tint = PrismaPalette.NaranjaPrisma,
                    modifier = Modifier.size(24.dp),
                )

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = document.nombreArchivo,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Prisma.colors.onSurface,
                    )

                    Text(
                        text = if (
                            document.tipoMime.contains(
                                "pdf",
                                ignoreCase = true,
                            )
                        ) {
                            "Documento PDF"
                        } else {
                            "Imagen"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Prisma.colors.onSurface.copy(
                            alpha = 0.7f,
                        ),
                    )
                }

                IconButton(
                    onClick = onDelete,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Eliminar documento del historial",
                        tint = MaterialTheme.colorScheme.error.copy(
                            alpha = 0.8f,
                        ),
                    )
                }
            }

            HorizontalDivider(
                color = Prisma.colors.onSurface.copy(
                    alpha = 0.1f,
                ),
            )

            Text(
                text = "Consulta los importes en el resumen del estado " +
                        "y los planes de pago en Análisis.",
                style = MaterialTheme.typography.bodySmall,
                color = Prisma.colors.onSurface.copy(
                    alpha = 0.7f,
                ),
            )
        }
    }
}

/**
 * Distribución de escritorio.
 *
 * Izquierda:
 * - Representación visual de la tarjeta.
 * - Selección de PDF o imágenes.
 *
 * Derecha:
 * - Resultado automático.
 * - Historial anterior.
 */
@Composable
private fun DesktopCardDetail(
    state: CardDetailUiState,
    card: CardDto,
    onBack: () -> Unit,
    onUploadPdf: () -> Unit,
    onUploadImage: () -> Unit,
    onDeleteDocument: (String) -> Unit,
    isReading: Boolean = false,
    modifier: Modifier = Modifier,
    readerContent: @Composable () -> Unit = {},
) {
    val colors = Prisma.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.screenContainer(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Encabezado de escritorio.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.surface),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Regresar a tarjetas",
                        tint = colors.onSurface,
                    )
                }

                Column {
                    BrandLabel(
                        text = "Tarjetas · Detalle",
                        color = colors.onSurface.copy(
                            alpha = 0.6f,
                        ),
                    )

                    Text(
                        text = card.alias,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 28.sp,
                        ),
                        color = colors.onSurface,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Columna izquierda: tarjeta y selección de archivos.
                Column(
                    modifier = Modifier.weight(0.38f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CardFace(
                        card = card,
                    )

                    UploadPanel(
                        isUploading = state.isUploading || isReading,
                        onUploadPdf = onUploadPdf,
                        onUploadImage = onUploadImage,
                    )

                    // Mensajes relacionados con operaciones del historial.
                    state.message?.let { message ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    (
                                            if (state.isError) {
                                                colors.statusCritical
                                            } else {
                                                PrismaPalette.NaranjaPrisma
                                            }
                                            ).copy(
                                            alpha = 0.12f,
                                        ),
                                )
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 12.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = if (state.isError) {
                                    Icons.Filled.ErrorOutline
                                } else {
                                    Icons.Filled.CheckCircle
                                },
                                contentDescription = null,
                                tint = if (state.isError) {
                                    colors.statusCritical
                                } else {
                                    PrismaPalette.NaranjaAmbar
                                },
                            )

                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurface,
                            )
                        }
                    }
                }

                // Columna derecha: estado automático e historial anterior.
                Column(
                    modifier = Modifier.weight(0.62f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Contenido automático recibido desde CardDetailScreen.
                    readerContent()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Historial de documentos",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp,
                            ),
                            color = colors.onSurface,
                        )

                        if (state.documents.isNotEmpty()) {
                            Text(
                                text = state.documents.size.toString(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = PrismaPalette.Grafito,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(PrismaPalette.Dorado)
                                    .padding(
                                        horizontal = 10.dp,
                                        vertical = 2.dp,
                                    ),
                            )
                        }
                    }

                    Text(
                        text = "Archivos guardados anteriormente para esta tarjeta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface.copy(
                            alpha = 0.6f,
                        ),
                    )

                    when {
                        state.isLoading && state.documents.isEmpty() -> {
                            SoftCard(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = PrismaPalette.NaranjaPrisma,
                                    )
                                }
                            }
                        }

                        state.documents.isEmpty() -> {
                            SoftCard(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical = 48.dp,
                                            horizontal = 24.dp,
                                        ),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Description,
                                        contentDescription = null,
                                        tint = PrismaPalette.NaranjaPrisma.copy(
                                            alpha = 0.6f,
                                        ),
                                        modifier = Modifier.size(44.dp),
                                    )

                                    Text(
                                        text = "No hay archivos en este historial",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = colors.onSurface,
                                    )

                                    Text(
                                        text = "Los estados incorporados se consultan " +
                                                "en el resumen superior.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurface.copy(
                                            alpha = 0.6f,
                                        ),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }

                        else -> {
                            /*
                             * Presenta dos documentos por fila.
                             * Si la última fila tiene uno, reserva el espacio
                             * de la segunda columna para mantener la alineación.
                             */
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                state.documents.chunked(2).forEach { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        row.forEach { document ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight(),
                                            ) {
                                                ExtractedStatementCard(
                                                    document = document,
                                                    onDelete = {
                                                        onDeleteDocument(
                                                            document.id,
                                                        )
                                                    },
                                                )
                                            }
                                        }

                                        if (row.size == 1) {
                                            Spacer(
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Evita que la navegación cubra las últimas filas.
            Spacer(
                modifier = Modifier.height(bottomNavBarSpace),
            )
        }
    }
}

/**
 * Representación visual de la tarjeta en escritorio.
 *
 * Conserva el degradado, la proporción y los colores de Prisma.
 * Solamente muestra banco, alias y los últimos cuatro dígitos.
 */
@Composable
private fun CardFace(
    card: CardDto,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        PrismaPalette.Grafito,
                        PrismaPalette.Grafito,
                        PrismaPalette.NaranjaAmbar.copy(
                            alpha = 0.85f,
                        ),
                    ),
                ),
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = card.banco,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                ),
                color = PrismaPalette.Crema,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Icon(
                imageVector = Icons.Filled.CreditCard,
                contentDescription = null,
                tint = PrismaPalette.Dorado,
                modifier = Modifier.size(28.dp),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "•••• •••• •••• ${
                    card.ultimosCuatro?.takeIf {
                        it.isNotBlank()
                    } ?: "••••"
                }",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                ),
                color = PrismaPalette.Crema,
            )

            BrandLabel(
                text = card.alias,
                color = PrismaPalette.Dorado,
            )
        }
    }
}

/**
 * Panel de selección de archivos para escritorio.
 *
 * Mientras existe una operación en curso, muestra un indicador
 * y oculta los botones para evitar importaciones simultáneas.
 */
@Composable
private fun UploadPanel(
    isUploading: Boolean,
    onUploadPdf: () -> Unit,
    onUploadImage: () -> Unit,
) {
    val colors = Prisma.colors

    val dashColor = PrismaPalette.NaranjaPrisma.copy(
        alpha = 0.5f,
    )

    SoftCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .drawBehind {
                    // Borde punteado del panel, conservando el diseño actual.
                    drawRoundRect(
                        color = dashColor,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(10f, 8f),
                            ),
                        ),
                        cornerRadius = CornerRadius(
                            16.dp.toPx(),
                        ),
                    )
                }
                .padding(
                    horizontal = 20.dp,
                    vertical = 24.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isUploading) {
                CircularProgressIndicator(
                    color = PrismaPalette.NaranjaPrisma,
                )

                Text(
                    text = "Procesando documento…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface.copy(
                        alpha = 0.8f,
                    ),
                    textAlign = TextAlign.Center,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = PrismaPalette.NaranjaPrisma,
                    modifier = Modifier.size(40.dp),
                )

                Text(
                    text = "Sube tu estado de cuenta",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.onSurface,
                )

                Text(
                    text = "PDF o imágenes JPG/PNG del mismo estado de cuenta. " +
                            "El análisis comienza al seleccionarlos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface.copy(
                        alpha = 0.6f,
                    ),
                    textAlign = TextAlign.Center,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onUploadPdf,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrismaPalette.Dorado,
                            contentColor = PrismaPalette.Grafito,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )

                        Spacer(
                            modifier = Modifier.width(6.dp),
                        )

                        Text(
                            text = "Subir PDF",
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    OutlinedButton(
                        onClick = onUploadImage,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            width = 1.dp,
                            color = PrismaPalette.NaranjaPrisma,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = null,
                            tint = PrismaPalette.NaranjaAmbar,
                            modifier = Modifier.size(18.dp),
                        )

                        Spacer(
                            modifier = Modifier.width(6.dp),
                        )

                        Text(
                            text = "Subir imagen",
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}