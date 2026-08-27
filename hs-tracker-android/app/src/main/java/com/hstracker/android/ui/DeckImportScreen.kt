package com.hstracker.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DeckImportScreen(vm: DeckImportViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("HSTracker", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Incolla un deck code (dalla schermata Copia del client Hearthstone).",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Deck code") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.import(code) },
            enabled = code.isNotBlank() && !state.loading,
        ) { Text("Importa") }

        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null -> Text(
                "Errore: ${state.error}",
                color = MaterialTheme.colorScheme.error,
            )

            state.deck != null -> {
                Text(
                    "Formato: ${state.deck!!.format.name}  •  Totale: ${state.deck!!.totalCards}/30",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.resolved) { rc -> CardRow(rc) }
                }
            }
        }
    }
}

@Composable
private fun CardRow(rc: ResolvedCard) {
    val name = rc.card?.name ?: "dbfId ${rc.dbfId}"
    val cost = rc.card?.cost
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ManaGem(cost)
        Text(name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text("×${rc.count}", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ManaGem(cost: Int?) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(Color(0xFF1E88E5), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            cost?.toString() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}
