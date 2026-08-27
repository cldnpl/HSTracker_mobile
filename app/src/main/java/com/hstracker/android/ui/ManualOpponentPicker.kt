package com.hstracker.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstracker.android.data.Card

/**
 * Fallback quando non sai il mazzo dell'avversario né riesci ad associare
 * un archetipo: cerchi la carta per nome, la aggiungi a una lista "l'ho vista"
 * (assunto: 2 copie), poi durante la partita tap nell'overlay come al solito.
 */
@Composable
fun ManualOpponentPicker(vm: DeckImportViewModel) {
    val query by vm.opponentQuery.collectAsState()
    val results by vm.opponentSearchResults.collectAsState()
    val picked by vm.manualOpponent.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Traccia manualmente (fallback)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Cerca la carta per nome. Ogni carta aggiunta parte come 2 copie nel tracker; " +
                "durante la partita tocchi la riga nell'overlay quando la vedi giocata.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = query,
            onValueChange = vm::updateOpponentQuery,
            label = { Text("Cerca carta (es. \"fireball\")") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (query.isNotBlank() && results.isEmpty()) {
            Text(
                "Nessun risultato. Il DB carte potrebbe non essere ancora caricato — importa prima il tuo mazzo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        results.take(8).forEach { card ->
            SearchResultRow(
                card = card,
                alreadyPicked = picked.any { it.dbfId == card.dbfId },
                onAdd = { vm.addManualOpponent(card) },
            )
        }

        if (picked.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${picked.size} carte tracciate",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::clearManualOpponent) { Text("Svuota") }
            }
            picked.forEach { c ->
                PickedRow(card = c, onRemove = { vm.removeManualOpponent(c.dbfId) })
            }
        }
    }
}

@Composable
private fun SearchResultRow(card: Card, alreadyPicked: Boolean, onAdd: () -> Unit) {
    val bg = if (alreadyPicked) Color(0x11000000) else Color.Transparent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(4.dp))
            .clickable(enabled = !alreadyPicked, onClick = onAdd)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        ManaGem(card.cost)
        Text(
            text = card.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (alreadyPicked) "già aggiunta" else "+",
            style = MaterialTheme.typography.bodyMedium,
            color = if (alreadyPicked) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PickedRow(card: Card, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        ManaGem(card.cost)
        Text(
            text = card.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "×",
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .clickable(onClick = onRemove),
        )
    }
}

@Composable
private fun ManaGem(cost: Int?) {
    Box(
        modifier = Modifier
            .size(22.dp)
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
