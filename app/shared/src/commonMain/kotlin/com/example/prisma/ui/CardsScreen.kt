package com.example.prisma.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prisma.api.CardDto
import com.example.prisma.api.IncomeDto
import com.example.prisma.cards.CardUiState
import com.example.prisma.cards.CardViewModel
import com.example.prisma.components.SoftCard
import com.example.prisma.components.bottomNavBarSpace
import com.example.prisma.income.AmountRange
import com.example.prisma.income.IncomeEditorState
import com.example.prisma.income.IncomeFilter
import com.example.prisma.income.availableMonths
import com.example.prisma.income.availableYears
import com.example.prisma.income.monthLabel
import com.example.prisma.income.sumAmounts
import com.example.prisma.income.IncomeUiState
import com.example.prisma.income.IncomeViewModel
import kotlinx.coroutines.launch

private enum class FinancePanel(val title: String) { Cards("Tarjetas"), Incomes("Ingresos") }

private fun formatTime12h(time24h: String): String {
    val parts = time24h.split(":")
    if (parts.size != 2) return time24h
    val h = parts[0].toIntOrNull() ?: return time24h
    val m = parts[1].toIntOrNull() ?: return time24h
    val amPm = if (h >= 12) "PM" else "AM"
    val h12 = when (h % 12) {
        0 -> 12
        else -> h % 12
    }
    val minStr = if (m < 10) "0$m" else "$m"
    return "$h12:$minStr $amPm"
}

@Composable
fun CardsScreen(
    cardViewModel: CardViewModel,
    incomeViewModel: IncomeViewModel,
    onCardClick: (CardDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val panels = FinancePanel.entries
    val pagerState = rememberPagerState(initialPage = FinancePanel.Cards.ordinal) { panels.size }
    val desktop = isDesktopLayout

    val cardState by cardViewModel.state.collectAsStateWithLifecycle()
    val incomeState by incomeViewModel.state.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier.fillMaxSize().background(Prisma.colors.background), contentAlignment = Alignment.TopCenter) {
        if (desktop && maxWidth >= 1040.dp) {
            Column(Modifier.screenContainer(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(
                    "Tarjetas e ingresos",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
                    color = Prisma.colors.onSurface,
                )
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BrandLabel(FinancePanel.Cards.title, color = Prisma.colors.onSurface.copy(alpha = 0.6f))
                        CardPanel(cardState, cardViewModel, onCardClick)
                    }
                    Column(Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BrandLabel(FinancePanel.Incomes.title, color = Prisma.colors.onSurface.copy(alpha = 0.6f))
                        IncomePanel(incomeState, incomeViewModel)
                    }
                }
            }
            return@BoxWithConstraints
        }

        Column(
            modifier = Modifier
                .widthIn(max = if (desktop) 720.dp else 480.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(top = if (desktop) 32.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PanelSwitcher(pagerState, panels.map { it.title }, Modifier.padding(horizontal = 16.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp),
                pageSpacing = 16.dp,
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (panels[page]) {
                    FinancePanel.Cards -> CardPanel(cardState, cardViewModel, onCardClick)
                    FinancePanel.Incomes -> IncomePanel(incomeState, incomeViewModel)
                }
            }
        }
    }
}

@Composable
private fun PanelSwitcher(pagerState: PagerState, titles: List<String>, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(24.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(Prisma.colors.surface)
            .padding(4.dp)
    ) {
        val segment = maxWidth / titles.size
        val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
        Box(
            Modifier
                .offset(x = segment * position)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(PrismaPalette.Grafito)
        )
        Row(Modifier.fillMaxSize()) {
            titles.forEachIndexed { index, title ->
                val selected = pagerState.currentPage == index
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { scope.launch { pagerState.animateScrollToPage(index) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) PrismaPalette.Dorado else Prisma.colors.onSurface,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardPanel(state: CardUiState, viewModel: CardViewModel, onCardClick: (CardDto) -> Unit) {
    state.pendingDelete?.let { card ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("¿Eliminar tarjeta?") },
            text = { Text("Se eliminará la tarjeta \"${card.alias}\" (${card.banco}).") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text("Eliminar", color = Prisma.colors.statusCritical) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Cancelar", color = Prisma.colors.onSurface) }
            },
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (state.editingCard != null) "Editar tarjeta" else "Agregar tarjeta de crédito",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                OutlinedTextField(
                    value = state.bankInput,
                    onValueChange = viewModel::onBankChange,
                    label = { Text("Banco") },
                    placeholder = { Text("Ej. BBVA, Nu, Citibanamex") },
                    singleLine = true,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.aliasInput,
                    onValueChange = viewModel::onAliasChange,
                    label = { Text("Alias o Apodo") },
                    placeholder = { Text("Ej. Tarjeta Principal, Gastos") },
                    singleLine = true,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.lastFourInput,
                    onValueChange = viewModel::onLastFourChange,
                    label = { Text("Últimos 4 dígitos (opcional)") },
                    placeholder = { Text("1234") },
                    singleLine = true,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Selección de Día de Corte y Notificación Opcional
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayDropdownSelector(
                        label = "Día de corte (opcional)",
                        selectedDay = state.diaCorteInput,
                        onDaySelected = viewModel::onDiaCorteChange,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (state.diaCorteInput != null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Recordatorio de corte por notificación",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Prisma.colors.onSurface,
                            )
                            Switch(
                                checked = state.notificarCorte,
                                onCheckedChange = viewModel::onNotificarCorteChange,
                                enabled = !state.isSaving,
                            )
                        }

                        if (state.notificarCorte) {
                            var showPicker by remember { mutableStateOf(false) }
                            OutlinedButton(
                                onClick = { showPicker = true },
                                enabled = !state.isSaving,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Icon(Icons.Filled.Notifications, contentDescription = null, Modifier.size(18.dp), tint = PrismaPalette.NaranjaPrisma)
                                Spacer(Modifier.width(8.dp))
                                Text("Hora: ${formatTime12h(state.horaNotificacionCorteInput)}", color = Prisma.colors.onSurface)
                            }

                            if (showPicker) {
                                TimePickerDialog(
                                    title = "Hora de recordatorio de corte",
                                    initialTime = state.horaNotificacionCorteInput,
                                    onConfirm = { time ->
                                        viewModel.onHoraNotificacionCorteChange(time)
                                        showPicker = false
                                    },
                                    onDismiss = { showPicker = false },
                                )
                            }
                        }
                    }
                }

                // Selección de Día de Pago y Notificación Opcional
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayDropdownSelector(
                        label = "Día de pago (opcional)",
                        selectedDay = state.diaPagoInput,
                        onDaySelected = viewModel::onDiaPagoChange,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (state.diaPagoInput != null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Recordatorio de pago por notificación",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Prisma.colors.onSurface,
                            )
                            Switch(
                                checked = state.notificarPago,
                                onCheckedChange = viewModel::onNotificarPagoChange,
                                enabled = !state.isSaving,
                            )
                        }

                        if (state.notificarPago) {
                            var showPicker by remember { mutableStateOf(false) }
                            OutlinedButton(
                                onClick = { showPicker = true },
                                enabled = !state.isSaving,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Icon(Icons.Filled.Notifications, contentDescription = null, Modifier.size(18.dp), tint = Prisma.colors.statusHealthy)
                                Spacer(Modifier.width(8.dp))
                                Text("Hora: ${formatTime12h(state.horaNotificacionPagoInput)}", color = Prisma.colors.onSurface)
                            }

                            if (showPicker) {
                                TimePickerDialog(
                                    title = "Hora de recordatorio de pago",
                                    initialTime = state.horaNotificacionPagoInput,
                                    onConfirm = { time ->
                                        viewModel.onHoraNotificacionPagoChange(time)
                                        showPicker = false
                                    },
                                    onDismiss = { showPicker = false },
                                )
                            }
                        }
                    }
                }

                state.message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.isError) Prisma.colors.statusCritical else Prisma.colors.statusHealthy,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.editingCard != null) {
                        OutlinedButton(
                            onClick = viewModel::cancelEditing,
                            enabled = !state.isSaving,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text("Cancelar")
                        }
                    }
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = PrismaPalette.Dorado),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = PrismaPalette.Grafito)
                        } else {
                            Text(
                                if (state.editingCard != null) "Guardar cambios" else "Guardar tarjeta",
                                color = PrismaPalette.Grafito,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Lista de tarjetas
        Text(
            "Tarjetas registradas",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Prisma.colors.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
        SoftCard(Modifier.fillMaxWidth()) {
            when {
                state.isLoading && state.cards.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = PrismaPalette.NaranjaPrisma)
                }
                state.cards.isEmpty() -> Text(
                    "Aún no registras tarjetas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Prisma.colors.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
                else -> Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    state.cards.forEachIndexed { index, card ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Prisma.colors.onSurface.copy(alpha = 0.08f))
                        CardRow(
                            card = card,
                            enabled = !state.isSaving,
                            onClick = { onCardClick(card) },
                            onEdit = { viewModel.startEditing(card) },
                            onDelete = { viewModel.requestDelete(card) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(bottomNavBarSpace))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialTime: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val (h, m) = remember(initialTime) {
        val parts = initialTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val min = parts.getOrNull(1)?.toIntOrNull() ?: 0
        Pair(hour, min)
    }
    val timePickerState = rememberTimePickerState(
        initialHour = h,
        initialMinute = m,
        is24Hour = false,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val hStr = if (timePickerState.hour < 10) "0${timePickerState.hour}" else "${timePickerState.hour}"
                    val mStr = if (timePickerState.minute < 10) "0${timePickerState.minute}" else "${timePickerState.minute}"
                    onConfirm("$hStr:$mStr")
                }
            ) {
                Text("Aceptar", color = PrismaPalette.Dorado)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Prisma.colors.onSurface)
            }
        },
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timePickerState)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDropdownSelector(
    label: String,
    selectedDay: Int?,
    onDaySelected: (Int?) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedDay?.let { "Día $it" } ?: "Sin especificar",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Sin especificar", color = Prisma.colors.onSurface.copy(alpha = 0.6f)) },
                onClick = {
                    onDaySelected(null)
                    expanded = false
                },
            )
            (1..31).forEach { day ->
                DropdownMenuItem(
                    text = { Text("Día $day del mes") },
                    onClick = {
                        onDaySelected(day)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CardRow(card: CardDto, enabled: Boolean, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                card.alias,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Prisma.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${card.banco}${card.ultimosCuatro?.let { " •••• $it" } ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = Prisma.colors.onSurface.copy(alpha = 0.7f),
            )
            if (card.diaCorte != null || card.diaPago != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    card.diaCorte?.let { day ->
                        val timeStr = card.horaNotificacionCorte?.let { " (${formatTime12h(it)})" } ?: ""
                        Text(
                            "Corte: Día $day$timeStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrismaPalette.NaranjaPrisma,
                        )
                    }
                    card.diaPago?.let { day ->
                        val timeStr = card.horaNotificacionPago?.let { " (${formatTime12h(it)})" } ?: ""
                        Text(
                            "Pago: Día $day$timeStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = Prisma.colors.statusHealthy,
                        )
                    }
                }
            }
        }
        IconButton(onClick = onEdit, enabled = enabled) {
            Icon(Icons.Filled.Edit, contentDescription = "Editar tarjeta ${card.alias}", tint = Prisma.colors.onSurface.copy(alpha = 0.7f))
        }
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(Icons.Filled.Delete, contentDescription = "Eliminar tarjeta ${card.alias}", tint = Prisma.colors.statusCritical)
        }
    }
}

@Composable
private fun IncomePanel(state: IncomeUiState, viewModel: IncomeViewModel) {
    state.pendingDelete?.let { income ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("¿Eliminar ingreso?") },
            text = { Text("Se eliminará \"${income.nombre}\" por ${formatMxn(income.montoNeto)}, registrado el ${formatIsoDateTime(income.fechaRegistro)}.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text("Eliminar", color = Prisma.colors.statusCritical) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Cancelar", color = Prisma.colors.onSurface) }
            },
        )
    }

    state.editor?.let { editor ->
        IncomeEditDialog(editor = editor, ingresos = state.ingresos, viewModel = viewModel)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Ingresos de este mes", style = MaterialTheme.typography.bodyMedium, color = Prisma.colors.onSurface.copy(alpha = 0.7f))
                Text(
                    formatMxn(state.totalMes),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Prisma.colors.statusHealthy,
                )
            }
        }

        // En la web (pantalla ancha) los campos y el botón van en una sola línea; en móvil, apilados.
        val desktop = isDesktopLayout
        val nameField: @Composable (Modifier) -> Unit = { fieldModifier ->
            OutlinedTextField(
                value = state.nameInput,
                onValueChange = viewModel::onNameChange,
                label = { Text("Nombre del ingreso") },
                placeholder = { Text("Ej. Salario, Freelance, Renta") },
                singleLine = true,
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = fieldModifier,
            )
        }
        val amountField: @Composable (Modifier) -> Unit = { fieldModifier ->
            OutlinedTextField(
                value = state.amountInput,
                onValueChange = viewModel::onAmountChange,
                // En la fila de la web el campo es angosto: etiqueta corta para que no se parta en 2 líneas.
                label = { Text(if (desktop) "Monto (MXN)" else "Monto neto (MXN)", maxLines = 1) },
                prefix = { Text("$ ") },
                placeholder = { Text("0.00") },
                singleLine = true,
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.save() }),
                modifier = fieldModifier,
            )
        }
        val saveButton: @Composable (Modifier) -> Unit = { buttonModifier ->
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = buttonModifier,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = PrismaPalette.Dorado),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = PrismaPalette.Grafito)
                } else {
                    Text("Guardar ingreso", color = PrismaPalette.Grafito, fontWeight = FontWeight.Bold)
                }
            }
        }

        SoftCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(if (desktop) 20.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Agregar ingreso", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                if (desktop) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                        nameField(Modifier.weight(1.3f))
                        amountField(Modifier.weight(1f))
                        saveButton(Modifier.height(56.dp))
                    }
                } else {
                    nameField(Modifier.fillMaxWidth())
                    amountField(Modifier.fillMaxWidth())
                }
                state.message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.isError) Prisma.colors.statusCritical else Prisma.colors.statusHealthy,
                    )
                }
                if (!desktop) saveButton(Modifier.fillMaxWidth().height(48.dp))
            }
        }

        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Ingresos registrados",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Prisma.colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (state.ingresos.isNotEmpty()) {
                IncomeFilterButton(
                    filter = state.filter,
                    ingresos = state.ingresos,
                    onSelect = viewModel::setFilter,
                )
            }
        }
        val visible = remember(state.ingresos, state.filter) { state.ingresos.filter(state.filter::matches) }
        if (state.filter != IncomeFilter.All && state.ingresos.isNotEmpty()) {
            ActiveFilterBar(
                filter = state.filter,
                count = visible.size,
                total = remember(visible) { sumAmounts(visible) },
                onClear = { viewModel.setFilter(IncomeFilter.All) },
            )
        }
        SoftCard(Modifier.fillMaxWidth()) {
            when {
                state.isLoading && state.ingresos.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = PrismaPalette.NaranjaPrisma)
                }
                state.ingresos.isEmpty() -> Text(
                    "Aún no registras ingresos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Prisma.colors.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
                visible.isEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "No hay ingresos para \"${state.filter.label}\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Prisma.colors.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { viewModel.setFilter(IncomeFilter.All) }) {
                        Text("Ver todos", color = PrismaPalette.NaranjaAmbar, fontWeight = FontWeight.SemiBold)
                    }
                }
                desktop -> Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    IncomeTableHeader()
                    visible.forEach { income ->
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Prisma.colors.onSurface.copy(alpha = 0.08f))
                        IncomeTableRow(
                            income,
                            enabled = !state.isSaving,
                            onEdit = { viewModel.openEditor(income) },
                            onDelete = { viewModel.requestDelete(income) },
                        )
                    }
                }
                else -> Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    visible.forEachIndexed { index, income ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Prisma.colors.onSurface.copy(alpha = 0.08f))
                        IncomeRow(
                            income,
                            enabled = !state.isSaving,
                            onEdit = { viewModel.openEditor(income) },
                            onDelete = { viewModel.requestDelete(income) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(bottomNavBarSpace))
    }
}

@Composable
private fun IncomeRow(income: IncomeDto, enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                income.nombre,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Prisma.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatMxn(income.montoNeto),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = Prisma.colors.statusHealthy,
            )
            Text(
                formatIsoDateTime(income.fechaRegistro),
                style = MaterialTheme.typography.bodySmall,
                color = Prisma.colors.onSurface.copy(alpha = 0.6f),
            )
        }
        if (income.esAuditado) AuditedChip()
        IconButton(onClick = onEdit, enabled = enabled) {
            Icon(Icons.Filled.Edit, contentDescription = "Editar ingreso ${income.nombre}", tint = Prisma.colors.onSurface.copy(alpha = 0.7f))
        }
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(Icons.Filled.Delete, contentDescription = "Eliminar ingreso ${income.nombre}", tint = Prisma.colors.statusCritical)
        }
    }
}

/** Etiqueta "Auditado" (misma que en la lista de ingresos). */
@Composable
private fun AuditedChip() {
    Text(
        "Auditado",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = Prisma.colors.statusHealthy,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Prisma.colors.statusHealthy.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * Ventana emergente para editar ingresos.
 * Paso 1: el usuario elige qué ingreso editar. Paso 2: cambia nombre y monto.
 */
@Composable
private fun IncomeEditDialog(editor: IncomeEditorState, ingresos: List<IncomeDto>, viewModel: IncomeViewModel) {
    val colors = Prisma.colors
    val desktop = isDesktopLayout
    Dialog(
        onDismissRequest = viewModel::closeEditor,
        properties = DialogProperties(
            dismissOnClickOutside = !editor.isSaving,
            dismissOnBackPress = !editor.isSaving,
            usePlatformDefaultWidth = !desktop,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.surface,
            contentColor = colors.onSurface,
            shadowElevation = 12.dp,
            // Web: ancho máximo de 600 dp centrado. Móvil: igual que antes.
            modifier = if (desktop) Modifier.widthIn(max = 600.dp).fillMaxWidth() else Modifier.fillMaxWidth().widthIn(max = 460.dp),
        ) {
            Column(Modifier.padding(if (desktop) 28.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BrandLabel("Ingresos", color = PrismaPalette.NaranjaAmbar)

                val selected = editor.selected
                if (selected == null) {
                    // ---------- Paso 1: elegir ingreso ----------
                    Text(
                        "¿Qué ingreso quieres editar?",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.background)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        ingresos.forEachIndexed { index, income ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = colors.onSurface.copy(alpha = 0.08f))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) { viewModel.selectForEdit(income) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        income.nombre,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        formatMxn(income.montoNeto),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = colors.statusHealthy,
                                    )
                                    Text(
                                        formatIsoDateTime(income.fechaRegistro),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                                if (income.esAuditado) AuditedChip()
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = "Editar ${income.nombre}",
                                    tint = PrismaPalette.NaranjaPrisma,
                                )
                            }
                        }
                    }
                    TextButton(onClick = viewModel::closeEditor, modifier = Modifier.align(Alignment.End)) {
                        Text("Cerrar", color = colors.onSurface)
                    }
                } else {
                    // ---------- Paso 2: editar ----------
                    Text(
                        "Editar ingreso",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                    )
                    Text(
                        "Registrado el ${formatIsoDateTime(selected.fechaRegistro)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface.copy(alpha = 0.6f),
                    )
                    val editName: @Composable (Modifier) -> Unit = { fieldModifier ->
                        OutlinedTextField(
                            value = editor.nameInput,
                            onValueChange = viewModel::onEditNameChange,
                            label = { Text("Nombre del ingreso") },
                            placeholder = { Text("Ej. Salario, Freelance, Renta") },
                            singleLine = true,
                            enabled = !editor.isSaving,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                            modifier = fieldModifier,
                        )
                    }
                    val editAmount: @Composable (Modifier) -> Unit = { fieldModifier ->
                        OutlinedTextField(
                            value = editor.amountInput,
                            onValueChange = viewModel::onEditAmountChange,
                            label = { Text("Monto neto (MXN)") },
                            prefix = { Text("$ ") },
                            placeholder = { Text("0.00") },
                            singleLine = true,
                            enabled = !editor.isSaving,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { viewModel.saveEdit() }),
                            modifier = fieldModifier,
                        )
                    }
                    if (desktop) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            editName(Modifier.weight(1.3f))
                            editAmount(Modifier.weight(1f))
                        }
                    } else {
                        editName(Modifier.fillMaxWidth())
                        editAmount(Modifier.fillMaxWidth())
                    }
                    if (selected.esAuditado) {
                        Text(
                            "Este ingreso está auditado. Si cambias el monto, dejará de estarlo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrismaPalette.NaranjaAmbar,
                        )
                    }
                    editor.error?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.statusCritical)
                    }
                    val saveEditButton: @Composable (Modifier) -> Unit = { buttonModifier ->
                        Button(
                            onClick = viewModel::saveEdit,
                            enabled = !editor.isSaving,
                            modifier = buttonModifier,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = PrismaPalette.Dorado),
                        ) {
                            if (editor.isSaving) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = PrismaPalette.Grafito)
                            } else {
                                Text("Guardar cambios", color = PrismaPalette.Grafito, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    val chooseOther: @Composable () -> Unit = {
                        if (ingresos.size > 1) {
                            TextButton(onClick = viewModel::backToSelection, enabled = !editor.isSaving) {
                                Text("Elegir otro ingreso", color = PrismaPalette.NaranjaAmbar, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Spacer(Modifier)
                        }
                    }
                    val cancel: @Composable () -> Unit = {
                        TextButton(onClick = viewModel::closeEditor, enabled = !editor.isSaving) {
                            Text("Cancelar", color = colors.onSurface)
                        }
                    }
                    if (desktop) {
                        // Web: acciones en una fila, el botón principal a la derecha.
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            chooseOther()
                            Spacer(Modifier.weight(1f))
                            cancel()
                            Spacer(Modifier.width(8.dp))
                            saveEditButton(Modifier.height(44.dp).widthIn(min = 180.dp))
                        }
                    } else {
                        saveEditButton(Modifier.fillMaxWidth().height(48.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            chooseOther()
                            cancel()
                        }
                    }
                }
            }
        }
    }
}

private enum class FilterMenuLevel { Root, Month, Year, Amount }

/** Botón con lista desplegable para filtrar los ingresos por mes, año o monto. */
@Composable
private fun IncomeFilterButton(filter: IncomeFilter, ingresos: List<IncomeDto>, onSelect: (IncomeFilter) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(FilterMenuLevel.Root) }
    val months = remember(ingresos) { availableMonths(ingresos) }
    val years = remember(ingresos) { availableYears(ingresos) }
    val active = filter != IncomeFilter.All

    fun choose(f: IncomeFilter) {
        onSelect(f)
        expanded = false
    }

    Box {
        OutlinedButton(
            onClick = { level = FilterMenuLevel.Root; expanded = true },
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, PrismaPalette.NaranjaPrisma),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (active) PrismaPalette.NaranjaPrisma.copy(alpha = 0.12f) else Color.Transparent,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.heightIn(min = 36.dp),
        ) {
            Icon(Icons.Filled.FilterList, contentDescription = null, tint = PrismaPalette.NaranjaAmbar, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (active) filter.label else "Filtrar",
                color = Prisma.colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Abrir filtros", tint = Prisma.colors.onSurface.copy(alpha = 0.7f))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = Prisma.colors.surface,
            // Web: ancho fijo para que el menú no cambie de tamaño al entrar a cada categoría.
            modifier = if (isDesktopLayout) Modifier.heightIn(max = 380.dp).width(260.dp) else Modifier.heightIn(max = 380.dp),
        ) {
            @Composable
            fun Header(title: String) {
                DropdownMenuItem(
                    text = { BrandLabel(title, color = PrismaPalette.NaranjaAmbar) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Prisma.colors.onSurface) },
                    onClick = { level = FilterMenuLevel.Root },
                )
                HorizontalDivider(color = Prisma.colors.onSurface.copy(alpha = 0.08f))
            }

            @Composable
            fun Option(text: String, option: IncomeFilter) {
                val selected = filter == option
                DropdownMenuItem(
                    text = {
                        Text(
                            text,
                            color = Prisma.colors.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    trailingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, contentDescription = "Seleccionado", tint = PrismaPalette.NaranjaPrisma) }
                    } else null,
                    onClick = { choose(option) },
                )
            }

            @Composable
            fun Category(text: String, icon: ImageVector, target: FilterMenuLevel) {
                DropdownMenuItem(
                    text = { Text(text, color = Prisma.colors.onSurface) },
                    leadingIcon = { Icon(icon, contentDescription = null, tint = PrismaPalette.NaranjaAmbar) },
                    trailingIcon = { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Prisma.colors.onSurface.copy(alpha = 0.6f)) },
                    onClick = { level = target },
                )
            }

            when (level) {
                FilterMenuLevel.Root -> {
                    Option("Todos los ingresos", IncomeFilter.All)
                    HorizontalDivider(color = Prisma.colors.onSurface.copy(alpha = 0.08f))
                    Category("Por mes", Icons.Filled.CalendarMonth, FilterMenuLevel.Month)
                    Category("Por año", Icons.Filled.DateRange, FilterMenuLevel.Year)
                    Category("Por monto", Icons.Filled.Payments, FilterMenuLevel.Amount)
                }
                FilterMenuLevel.Month -> {
                    Header("Por mes")
                    months.forEach { Option(monthLabel(it), IncomeFilter.Month(it)) }
                }
                FilterMenuLevel.Year -> {
                    Header("Por año")
                    years.forEach { Option(it, IncomeFilter.Year(it)) }
                }
                FilterMenuLevel.Amount -> {
                    Header("Por monto")
                    AmountRange.entries.forEach { Option(it.label, IncomeFilter.Amount(it)) }
                }
            }
        }
    }
}

/** Resumen del filtro activo: cuántos ingresos coinciden, su suma y la opción de quitarlo. */
@Composable
private fun ActiveFilterBar(filter: IncomeFilter, count: Int, total: String, onClear: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PrismaPalette.NaranjaPrisma.copy(alpha = 0.10f))
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                filter.label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Prisma.colors.onSurface,
            )
            Text(
                "${if (count == 1) "1 ingreso" else "$count ingresos"} · ${formatMxn(total)}",
                style = MaterialTheme.typography.bodySmall,
                color = Prisma.colors.onSurface.copy(alpha = 0.7f),
            )
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Filled.Close, contentDescription = "Quitar filtro", tint = Prisma.colors.onSurface.copy(alpha = 0.7f))
        }
    }
}

// ---------- Tabla de ingresos (solo web, pantalla ancha) ----------

private const val COL_NAME = 1.6f
private const val COL_AMOUNT = 1f
private const val COL_DATE = 1.2f

@Composable
private fun IncomeTableHeader() {
    val ink = Prisma.colors.onSurface.copy(alpha = 0.55f)
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandLabel("Concepto", color = ink, modifier = Modifier.weight(COL_NAME))
        BrandLabel("Monto", color = ink, modifier = Modifier.weight(COL_AMOUNT))
        BrandLabel("Registro", color = ink, modifier = Modifier.weight(COL_DATE))
        BrandLabel("Acciones", color = ink, modifier = Modifier.width(96.dp))
    }
}

@Composable
private fun IncomeTableRow(income: IncomeDto, enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interaction)
            .background(if (hovered) PrismaPalette.NaranjaPrisma.copy(alpha = 0.06f) else Color.Transparent)
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(COL_NAME), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                income.nombre,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Prisma.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (income.esAuditado) AuditedChip()
        }
        Text(
            formatMxn(income.montoNeto),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = Prisma.colors.statusHealthy,
            modifier = Modifier.weight(COL_AMOUNT),
        )
        Text(
            formatIsoDateTime(income.fechaRegistro),
            style = MaterialTheme.typography.bodyMedium,
            color = Prisma.colors.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(COL_DATE),
        )
        Row(Modifier.width(96.dp)) {
            IconButton(onClick = onEdit, enabled = enabled) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar ingreso ${income.nombre}", tint = Prisma.colors.onSurface.copy(alpha = 0.7f))
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar ingreso ${income.nombre}", tint = Prisma.colors.statusCritical)
            }
        }
    }
}
