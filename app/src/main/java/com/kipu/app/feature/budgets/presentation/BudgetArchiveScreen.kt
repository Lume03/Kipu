package com.kipu.app.feature.budgets.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.theme.rememberPlanningColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BudgetArchiveScreen(items: List<BudgetListItem>, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val colors = rememberPlanningColors()
    Scaffold(modifier = Modifier.testTag("budget_archive_screen"), containerColor = colors.background,
        topBar = { TopAppBar(title = { Text("Finalizados", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver a presupuestos") } }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Conservan su historial. No cuentan para tu límite.",
                style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted, modifier = Modifier.padding(vertical = 8.dp)) }
            if (items.isEmpty()) item { Text("Todavía no tienes presupuestos finalizados.", Modifier.padding(vertical = 24.dp)) }
            items(items, key = { it.id }) { item -> BudgetSummaryCard(item, onOpen = { onOpen(item.id) }) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
