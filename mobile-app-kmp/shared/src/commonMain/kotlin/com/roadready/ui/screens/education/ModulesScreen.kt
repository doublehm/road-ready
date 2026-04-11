package com.roadready.ui.screens.education

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadready.data.model.Module
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.theme.*
import org.koin.compose.koinInject

@Composable
fun ModulesScreen(
    onSelectModule: (Module) -> Unit,
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    var isLoading by remember { mutableStateOf(true) }
    var modules by remember { mutableStateOf<List<Module>>(emptyList()) }

    LaunchedEffect(Unit) {
        apiClient.getModules().onSuccess { modules = it }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Primary) }
            Text("Learning Modules", style = MaterialTheme.typography.headlineMedium)
        }

        if (isLoading) { LoadingOverlay(); return }

        if (modules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📚", style = MaterialTheme.typography.displayLarge)
                    Text("No modules available", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(modules, key = { it.id }) { module ->
                    ModuleCard(module) { onSelectModule(module) }
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(module: Module, onClick: () -> Unit) {
    val isCompleted = module.isCompleted
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                color = if (isCompleted) Accent.copy(alpha = 0.2f) else Primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (isCompleted) "✅" else "📘",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(module.title, style = MaterialTheme.typography.titleMedium)
                module.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 2)
                }
                module.quizCount?.let { count ->
                    if (count > 0) {
                        Text("$count quiz${if (count > 1) "zes" else ""}", style = MaterialTheme.typography.labelSmall, color = Warning)
                    }
                }
            }
            if (isCompleted) {
                Surface(color = Accent.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text("Done", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium, color = Accent)
                }
            }
        }
    }
}
