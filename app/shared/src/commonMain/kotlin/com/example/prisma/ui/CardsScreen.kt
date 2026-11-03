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
import com.example.prisma.api.IncomeDto
import com.example.prisma.components.BottomNavBarReservedSpace
import com.example.prisma.components.SoftCard
import com.example.prisma.income.IncomeUiState
import com.example.prisma.income.IncomeViewModel
import kotlinx.coroutines.launch

private enum class FinancePanel(val title: String) { Cards("Tarjetas"), Incomes("Ingresos") }

/**
 * Sección "Tarjetas": paneles que el usuario desliza horizontalmente.
 * Panel 1: agregar tarjetas de crédito (pendiente). Panel 2: agregar ingresos.
 */
@Composable
fun CardsScreen(incomeViewModel: IncomeViewModel, modifier: Modifier = Modifier) {
    val panels = FinancePanel.entries
    // Mientras el panel de tarjetas no esté listo, se abre directamente en Ingresos.
    val pagerState = rememberPagerState(initialPage = FinancePanel.Incomes.ordinal) { panels.size }

    Box(modifier.fillMaxSize().background(Prisma.colors.background), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(top = 12.dp),
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
                    FinancePanel.Cards -> CardsPlaceholderPanel()
                    FinancePanel.Incomes -> {
                        val state by incomeViewModel.state.collectAsStateWithLifecycle()
                        IncomePanel(state, incomeViewModel)
                    }
                }
            }
        }
    }
}

/** Selector "Tarjetas | Ingresos": su indicador sigue al dedo mientras se desliza el panel. */
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
private fun CardsPlaceholderPanel() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SoftCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.CreditCard, contentDescription = null, tint = PrismaPalette.NaranjaPrisma, modifier = Modifier.size(48.dp))
                Text(
                    "Agregar tarjeta de crédito",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Prisma.colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Muy pronto podrás registrar tus tarjetas: banco, alias y últimos 4 dígitos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Prisma.colors.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Desliza para registrar tus ingresos →",
                    style = MaterialTheme.typography.labelLarge,
                    color = Prisma.colors.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.height(BottomNavBarReservedSpace))
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
        // Total del mes
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

        // Formulario
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

        // Lista de ingresos
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

        Spacer(Modifier.height(BottomNavBarReservedSpace))
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
