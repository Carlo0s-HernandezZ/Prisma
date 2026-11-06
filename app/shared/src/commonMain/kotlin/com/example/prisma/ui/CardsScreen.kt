package com.example.prisma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prisma.api.CardDto
import com.example.prisma.api.IncomeDto
import com.example.prisma.cards.CardUiState
import com.example.prisma.cards.CardViewModel
import com.example.prisma.components.SoftCard
import com.example.prisma.components.bottomNavBarSpace
import com.example.prisma.income.IncomeUiState
import com.example.prisma.income.IncomeViewModel
import kotlinx.coroutines.launch

private enum class FinancePanel(val title: String) { Cards("Tarjetas"), Incomes("Ingresos") }

@Composable
fun CardsScreen(
    cardViewModel: CardViewModel,
    incomeViewModel: IncomeViewModel,
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
                        CardPanel(cardState, cardViewModel)
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
                    FinancePanel.Cards -> CardPanel(cardState, cardViewModel)
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

@Composable
private fun CardPanel(state: CardUiState, viewModel: CardViewModel) {
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
        // Formulario
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
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

@Composable
private fun CardRow(card: CardDto, enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
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

        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Agregar ingreso", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                OutlinedTextField(
                    value = state.nameInput,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Nombre del ingreso") },
                    placeholder = { Text("Ej. Salario, Freelance, Renta") },
                    singleLine = true,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.amountInput,
                    onValueChange = viewModel::onAmountChange,
                    label = { Text("Monto neto (MXN)") },
                    prefix = { Text("$ ") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.isError) Prisma.colors.statusCritical else Prisma.colors.statusHealthy,
                    )
                }
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
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
        }

        Text(
            "Ingresos registrados",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Prisma.colors.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
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
                else -> Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    state.ingresos.forEachIndexed { index, income ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Prisma.colors.onSurface.copy(alpha = 0.08f))
                        IncomeRow(income, enabled = !state.isSaving, onDelete = { viewModel.requestDelete(income) })
                    }
                }
            }
        }

        Spacer(Modifier.height(bottomNavBarSpace))
    }
}

@Composable
private fun IncomeRow(income: IncomeDto, enabled: Boolean, onDelete: () -> Unit) {
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
        if (income.esAuditado) {
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
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(Icons.Filled.Delete, contentDescription = "Eliminar ingreso ${income.nombre}", tint = Prisma.colors.statusCritical)
        }
    }
}