package com.hstracker.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstracker.android.data.Archetype
import com.hstracker.android.data.HeroClass

@Composable
fun OpponentPickerDialog(
    vm: DeckImportViewModel,
    onDismiss: () -> Unit,
) {
    val archetypes by vm.archetypesForClass.collectAsState()
    var selectedClass by remember { mutableStateOf<HeroClass?>(null) }

    LaunchedEffect(selectedClass) {
        selectedClass?.let(vm::loadArchetypesFor)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Che classe è l'avversario?") },
        text = {
            Column {
                // Grid 3xN di classi
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 220.dp),
                ) {
                    items(HeroClass.entries.toList()) { hero ->
                        ClassChip(
                            hero = hero,
                            selected = selectedClass == hero,
                            onClick = { selectedClass = hero },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                when {
                    selectedClass == null -> Text(
                        "Seleziona una classe per vedere gli archetipi caricati.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    archetypes.isEmpty() -> Text(
                        "Nessun archetipo per ${selectedClass!!.display} in archetypes.json. " +
                            "Aggiungine uno dalla tier list (istruzioni nel file).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.heightIn(max = 200.dp),
                    ) {
                        items(archetypes) { arch ->
                            ArchetypeCard(arch = arch, onClick = {
                                vm.applyOpponentArchetype(arch)
                                onDismiss()
                            })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Chiudi") }
        },
    )
}

@Composable
private fun ClassChip(hero: HeroClass, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .border(
                if (selected) 2.dp else 0.dp,
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            hero.display,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ArchetypeCard(arch: Archetype, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                arch.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                arch.format,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
