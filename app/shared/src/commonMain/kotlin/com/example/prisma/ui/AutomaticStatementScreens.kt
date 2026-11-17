package com.example.prisma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.example.prisma.api.CardPaymentPlan
import com.example.prisma.api.StatementDetectionStatus
import com.example.prisma.api.StatementPurchaseType
import com.example.prisma.budget.BudgetViewModel
import com.example.prisma.cards.*
import com.example.prisma.components.SoftCard
import com.example.prisma.components.bottomNavBarSpace
import kotlinx.coroutines.launch

@Composable
fun AutomaticStatementSection(
    viewModel: AutomaticStatementViewModel,
    onViewPlans: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy = state.isLoading || state.isReading || state.isResolving
    val current = state.current

    var details by rememberSaveable(current?.id) {
        mutableStateOf(false)
    }
    var history by remember { mutableStateOf(false) }

    SoftCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    Modifier.size(24.dp),
                    color = PrismaPalette.NaranjaPrisma,
                )
                Text(
                    if (state.isReading) {
                        "Analizando estado…"
                    } else "Actualizando…",
                )
            }

            state.error?.let {
                Text(it, color = Prisma.colors.statusCritical)
            }

            if (current == null) {
                Text(
                    "Agrega tu estado de cuenta",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Usa + para seleccionar un PDF o las imágenes del mismo estado.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                val detection = current.deteccion
                val fields = detection.campos

                Text(
                    if (detection.estado == StatementDetectionStatus.LISTO) {
                        "Estado incorporado"
                    } else "Se necesita una aclaración",
                    color = PrismaPalette.NaranjaAmbar,
                    style = MaterialTheme.typography.bodySmall,
                )

                Text(
                    detection.detalle.tipo.statementLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    fields.pagoParaNoGenerarIntereses?.let(::formatMxn)
                        ?: "Importe por identificar",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    "Importe del periodo para no generar intereses",
                    style = MaterialTheme.typography.bodySmall,
                )

                AutoStatementRow(
                    "Fecha límite",
                    fields.fechaLimitePago?.let(::formatIsoDateTime),
                )

                detection.detalle.cuotasMsi
                    ?.takeIf { it != "0.00" }
                    ?.let {
                        AutoStatementRow(
                            "MSI incluidas en el periodo",
                            formatMxn(it),
                        )
                    }

                Text(
                    "Estado al " +
                            (
                                    fields.fechaCorte?.let(::formatIsoDateTime)
                                        ?: "corte por identificar"
                                    ),
                    style = MaterialTheme.typography.bodySmall,
                )

                if (detection.estado != StatementDetectionStatus.LISTO) {
                    Text(
                        detection.pregunta
                            ?: "Agrega una copia más clara de la página del resumen.",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    detection.opcionesPago.forEach { amount ->
                        OutlinedButton(
                            onClick = { viewModel.resolve(amount) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Mi banco confirmó " + formatMxn(amount))
                        }
                    }
                }

                Button(
                    onClick = onViewPlans,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrismaPalette.Dorado,
                        contentColor = PrismaPalette.Grafito,
                    ),
                ) {
                    Text(
                        "Ver en Análisis",
                        fontWeight = FontWeight.Bold,
                    )
                }

                TextButton(onClick = { details = !details }) {
                    Text(if (details) "Ocultar detalle" else "Ver detalle")
                }

                if (details) {
                    AutoStatementRow(
                        "Compras a un pago",
                        detection.detalle.comprasUnPago?.let(::formatMxn),
                    )
                    AutoStatementRow(
                        "Pago mínimo",
                        fields.pagoMinimo?.let(::formatMxn),
                    )
                    AutoStatementRow(
                        "Mínimo + mensualidades",
                        detection.detalle.minimoConCuotas?.let(::formatMxn),
                    )
                    AutoStatementRow(
                        "Saldo total al corte",
                        fields.saldoTotal?.let(::formatMxn),
                    )
                    AutoStatementRow(
                        "Saldo a meses",
                        detection.detalle.saldoMeses?.let(::formatMxn),
                    )

                    detection.detalle.planes.forEach { plan ->
                        HorizontalDivider()

                        Text(
                            plan.descripcion,
                            fontWeight = FontWeight.Medium,
                        )

                        Text(
                            (
                                    if (plan.sinIntereses) {
                                        "MSI"
                                    } else "A meses con intereses"
                                    ) +
                                    " · pago ${plan.pagoActual} de ${plan.totalPagos}",
                            style = MaterialTheme.typography.bodySmall,
                        )

                        AutoStatementRow(
                            "Mensualidad",
                            formatMxn(plan.cuota),
                        )
                        AutoStatementRow(
                            "Saldo pendiente",
                            formatMxn(plan.saldoPendiente),
                        )
                    }

                    detection.avisos.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (state.records.size > 1) {
                Box {
                    TextButton(
                        onClick = { history = true },
                        enabled = !busy,
                    ) {
                        Text("Ver estados guardados")
                    }

                    DropdownMenu(
                        expanded = history,
                        onDismissRequest = { history = false },
                    ) {
                        state.records.forEach { record ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        record.deteccion.campos.fechaCorte
                                            ?.let(::formatIsoDateTime)
                                            ?: "Lectura por completar",
                                    )
                                },
                                onClick = {
                                    viewModel.selectStatement(record.id)
                                    history = false
                                },
                            )
                        }
                    }
                }
            }

            if (state.error != null) {
                TextButton(
                    onClick = viewModel::reload,
                    enabled = !busy,
                ) {
                    Text("Actualizar estados")
                }
            }
        }
    }
}

internal fun StatementPurchaseType.statementLabel(): String = when (this) {
    StatementPurchaseType.UN_PAGO -> "Compras a un pago"
    StatementPurchaseType.MSI -> "Compras a MSI"
    StatementPurchaseType.CON_INTERESES -> "Compras a meses con intereses"
    StatementPurchaseType.MIXTO -> "MSI y otros cargos"
    StatementPurchaseType.DESCONOCIDO -> "Tipo por identificar"
}

@Composable
private fun AutoStatementRow(
    label: String,
    value: String?,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            value ?: "Por identificar",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun CardPlansScreen(
    viewModel: BudgetViewModel,
    token: String,
    onSessionExpired: () -> Unit,
    /** false cuando la pantalla va dentro de otra que ya muestra el título (pestañas de Análisis). */
    showTitle: Boolean = true,
) {
    val budget by viewModel.state.collectAsStateWithLifecycle()

    val plans = composeViewModel(key = "card-plans-$token") {
        CardPlansViewModel(KtorCardPlanRepository(token))
    }

    val state by plans.state.collectAsStateWithLifecycle()
    val currentOnSessionExpired by rememberUpdatedState(onSessionExpired)
    var settings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.sessionExpired, budget.sessionExpired) {
        if (state.sessionExpired || budget.sessionExpired) {
            currentOnSessionExpired()
        }
    }

    LaunchedEffect(budget.periodo, budget.version) {
        plans.load(budget.periodo)
    }

    LaunchedEffect(budget.pendingPeriod) {
        if (budget.pendingPeriod != null) settings = true
    }

    if (settings) {
        Column(
            Modifier.fillMaxSize().background(Prisma.colors.background),
        ) {
            TextButton(
                onClick = {
                    if (budget.dirty || budget.editor != null) {
                        viewModel.reload()
                    } else settings = false
                },
            ) {
                Text("Volver al plan")
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnalysisScreen(viewModel)
            }
        }
        return
    }

    Column(
        Modifier.fillMaxSize()
            .background(Prisma.colors.background)
            .verticalScroll(rememberScrollState())
            .screenContainer(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showTitle) {
            Text(
                "Análisis",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        PrismaMonthField(
            budget.periodo,
            viewModel::selectPeriod,
            "Mes del plan",
            enabled = !state.isLoading,
        )

        if (state.isLoading) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth(),
                color = PrismaPalette.NaranjaPrisma,
            )
        }

        state.error?.let {
            Text(it, color = Prisma.colors.statusCritical)
        }

        val data = state.data

        if (data != null) {
            SoftCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Total programado",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    Text(
                        data.totalRecomendado?.let(::formatMxn)
                            ?: "Pendiente de calcular",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Text(
                        "Plan estimado según tus ingresos y disponibilidad",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    if (data.tarjetas.any { it.accion != null }) {
                        Text(
                            "Hay tarjetas pendientes de aclarar.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (data.tarjetas.isEmpty()) {
                Text(
                    "Agrega un estado de cuenta en Tarjetas para preparar tu plan.",
                )
            }

            data.tarjetas.forEach { plan ->
                key(plan.tarjetaId) {
                    CardPlanPanel(
                        plan = plan,
                        token = token,
                        busy = state.isLoading,
                        onUnpaid = { plans.confirmUnpaid(plan) },
                        onResolve = { plans.resolvePayment(plan, it) },
                        onReload = { plans.load(budget.periodo) },
                        onSettings = { settings = true },
                        onSessionExpired = { currentOnSessionExpired() },
                    )
                }
            }

            if (!data.presupuestoConfigurado) {
                Button(
                    onClick = { settings = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrismaPalette.Dorado,
                        contentColor = PrismaPalette.Grafito,
                    ),
                ) {
                    Text(
                        "Configurar disponibilidad",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            var detail by rememberSaveable(budget.periodo) {
                mutableStateOf(false)
            }

            TextButton(onClick = { detail = !detail }) {
                Text(
                    if (detail) {
                        "Ocultar detalle del plan"
                    } else "Ver detalle del plan",
                )
            }

            if (detail) {
                data.avisos.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        TextButton(
            onClick = { plans.load(budget.periodo) },
            enabled = !state.isLoading,
        ) {
            Text("Actualizar plan")
        }

        TextButton(onClick = { settings = true }) {
            Text("Dinero, gastos y reserva")
        }

        Spacer(Modifier.height(bottomNavBarSpace))
    }
}

@Composable
private fun CardPlanPanel(
    plan: CardPaymentPlan,
    token: String,
    busy: Boolean,
    onUnpaid: () -> Unit,
    onResolve: (String) -> Unit,
    onReload: () -> Unit,
    onSettings: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by rememberSaveable { mutableStateOf(false) }
    var payments by rememberSaveable { mutableStateOf(false) }

    val repository = remember(token) {
        KtorAutomaticStatementRepository(token)
    }

    val picker = rememberStatementPicker(
        onFilesSelected = { files ->
            if (!uploading) {
                uploading = true

                scope.launch {
                    repository.importFiles(plan.tarjetaId, files).fold(
                        onSuccess = {
                            uploading = false
                            error = null
                            onReload()
                        },
                        onFailure = {
                            uploading = false
                            error = it.message

                            if (
                                (it as? AutomaticStatementException)?.status == 401
                            ) onSessionExpired()
                        },
                    )
                }
            }
        },
        onError = { error = it },
    )

    SoftCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                plan.alias,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                plan.tipo.statementLabel(),
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                plan.recomendado?.let(::formatMxn)
                    ?: plan.objetivo?.let(::formatMxn)
                    ?: "Importe por identificar",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                if (plan.recomendado != null) {
                    "Pago recomendado"
                } else "Importe del estado · plan pendiente",
                style = MaterialTheme.typography.bodySmall,
            )

            plan.estado?.campos?.fechaLimitePago?.let {
                Text(
                    "Fecha límite: " + formatIsoDateTime(it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            plan.cuotasMsi?.takeIf { it != "0.00" }?.let {
                Text(
                    "MSI incluidas: " + formatMxn(it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            plan.programacion.forEach { item ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Apartar el " + formatIsoDateTime(item.fecha),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        formatMxn(item.monto),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            val missing = plan.faltante

            if (
                plan.recomendado != null &&
                missing != null &&
                missing != "0.00"
            ) {
                Text(
                    "Faltan " + formatMxn(missing) +
                            " para cubrir el objetivo del estado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PrismaPalette.NaranjaAmbar,
                )

                Text(
                    plan.avisos.firstOrNull {
                        it.contains("no alcanza el mínimo")
                    } ?: "El pago parcial puede generar intereses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PrismaPalette.NaranjaAmbar,
                )
            }

            plan.avisos.firstOrNull {
                it.contains("fecha límite ya pasó")
            }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = PrismaPalette.NaranjaAmbar,
                )
            }

            if (plan.accion != null) {
                plan.avisos.firstOrNull()?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = PrismaPalette.NaranjaAmbar,
                    )
                }
            }

            when (plan.accion) {
                "CONFIGURAR_DISPONIBILIDAD",
                "AJUSTAR_DISPONIBILIDAD" -> {
                    TextButton(onClick = onSettings) {
                        Text("Revisar disponibilidad")
                    }
                }

                "CONFIRMAR_PAGOS" -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = onUnpaid,
                            enabled = !busy,
                        ) {
                            Text("Aún no he pagado")
                        }
                        TextButton(
                            onClick = { payments = true },
                            enabled = !busy,
                        ) {
                            Text("Sí, registrar pago")
                        }
                    }
                }

                "REVISAR_PAGOS" -> {
                    TextButton(onClick = { payments = true }) {
                        Text("Revisar pagos")
                    }
                }

                "REVISAR_ESTADO" -> {
                    plan.automatico?.deteccion?.opcionesPago
                        .orEmpty()
                        .forEach { amount ->
                            OutlinedButton(
                                onClick = { onResolve(amount) },
                                enabled = !busy,
                            ) {
                                Text(
                                    "Mi banco confirmó " + formatMxn(amount),
                                )
                            }
                        }
                }
            }

            if (
                plan.accion == "ACTUALIZAR_ESTADO" ||
                (
                        plan.accion == "REVISAR_ESTADO" &&
                                plan.automatico?.deteccion?.opcionesPago.isNullOrEmpty()
                        )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = picker.launchPdfPicker,
                        enabled = !uploading && !busy,
                    ) {
                        Text("Agregar PDF")
                    }

                    TextButton(
                        onClick = picker.launchImagePicker,
                        enabled = !uploading && !busy,
                    ) {
                        Text("Agregar imágenes")
                    }
                }
            }

            if (uploading) {
                CircularProgressIndicator(
                    Modifier.size(24.dp),
                    color = PrismaPalette.NaranjaPrisma,
                )
            }

            error?.let {
                Text(it, color = Prisma.colors.statusCritical)
            }

            TextButton(onClick = { detail = !detail }) {
                Text(if (detail) "Ocultar detalle" else "Ver detalle")
            }

            if (detail) {
                plan.objetivo?.let {
                    Text(
                        "Objetivo del periodo: " + formatMxn(it),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                plan.minimoSeguro?.let {
                    Text(
                        "Mínimo y cuotas pendientes: " + formatMxn(it),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                plan.faltante?.let {
                    Text(
                        "Falta para el objetivo: " + formatMxn(it),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                plan.avisos.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }

                if (plan.estado != null) {
                    TextButton(onClick = { payments = !payments }) {
                        Text("Pagos registrados")
                    }
                }
            }

            val financial = plan.estado

            if (payments && financial != null) {
                StatementPaymentsSection(
                    financial,
                    token,
                    onSessionExpired,
                    onReload,
                )
            }
        }
    }
}