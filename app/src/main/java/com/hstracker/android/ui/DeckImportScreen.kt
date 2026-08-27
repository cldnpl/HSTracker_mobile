package com.hstracker.android.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import com.hstracker.android.capture.CaptureService
import com.hstracker.android.capture.CaptureState
import com.hstracker.android.overlay.OverlayService

@Composable
fun DeckImportScreen(vm: DeckImportViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var showOpponentPicker by remember { mutableStateOf(false) }

    if (showOpponentPicker) {
        OpponentPickerDialog(vm = vm, onDismiss = { showOpponentPicker = false })
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        overlayGranted = Settings.canDrawOverlays(context)
        if (overlayGranted && vm.startGameSession()) OverlayService.start(context)
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* nice-to-have */ }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            CaptureService.start(context, result.resultCode, data)
        }
    }
    val captureRunning by CaptureState.running.collectAsState()
    val frameCount by CaptureState.frameCount.collectAsState()
    val lastFrame by CaptureState.lastFrameWxH.collectAsState()

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("HSTracker", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Incolla il tuo deck code (dal client Hearthstone o da HSReplay). " +
                "Se sai che mazzo pensi stia giocando l'avversario, incollane il codice sotto: " +
                "verrà mostrato nell'overlay come mazzo probabile.",
            style = MaterialTheme.typography.bodyMedium,
        )

        // --- Il tuo mazzo ---
        SectionTitle("Il tuo mazzo", accent = Color(0xFFB39DDB))
        DeckImportBlock(
            label = "Deck code",
            side = state.player,
            onCodeChange = vm::updatePlayerCode,
            onImport = { vm.importPlayer(state.player.code) },
        )

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        // --- Avversario ---
        SectionTitle("Avversario (probabile)", accent = Color(0xFFEF9A9A))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showOpponentPicker = true }) {
                Text("Scegli da archetipi meta")
            }
            if (state.opponent.deck != null || state.opponent.code.isNotBlank()) {
                TextButton(onClick = vm::clearOpponent) { Text("Rimuovi") }
            }
        }
        DeckImportBlock(
            label = "…oppure incolla un deck code",
            side = state.opponent,
            onCodeChange = vm::updateOpponentCode,
            onImport = { vm.importOpponent(state.opponent.code) },
        )

        Spacer(Modifier.height(8.dp))
        ManualOpponentPicker(vm = vm)

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        // --- Avvio partita ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (!Settings.canDrawOverlays(context)) {
                        overlaySettingsLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    } else if (vm.startGameSession()) {
                        OverlayService.start(context)
                    }
                },
                enabled = state.player.deck != null,
            ) { Text("Avvia partita") }

            OutlinedButton(onClick = {
                OverlayService.stop(context)
                vm.stopGameSession()
            }) { Text("Ferma") }
        }
        if (!overlayGranted) {
            Text(
                "Serve il permesso \"Sovrapponi ad altre app\" per l'overlay durante il gioco.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        // --- Fase 2 (beta): riconoscimento carte via MediaProjection ---
        SectionTitle("Riconoscimento carte (beta)", accent = Color(0xFF80DEEA))
        Text(
            "Infrastruttura di cattura schermo. Iterazione 1: verifica che i frame " +
                "arrivino. Le iterazioni successive aggiungeranno il matching della carta " +
                "pescata contro il DB artwork.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!captureRunning) {
                Button(onClick = {
                    val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                        as MediaProjectionManager
                    captureLauncher.launch(mpm.createScreenCaptureIntent())
                }) { Text("Attiva riconoscimento") }
            } else {
                OutlinedButton(onClick = { CaptureService.stop(context) }) {
                    Text("Ferma riconoscimento")
                }
            }
        }
        if (captureRunning) {
            Text(
                "Cattura attiva • frame ricevuti: $frameCount" +
                    (lastFrame?.let { " • ultimo: ${it.first}×${it.second}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(accent, CircleShape))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DeckImportBlock(
    label: String,
    side: DeckSideUiState,
    onCodeChange: (String) -> Unit,
    onImport: () -> Unit,
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = side.code,
        onValueChange = onCodeChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onImport,
            enabled = side.code.isNotBlank() && !side.loading,
        ) { Text("Importa") }
        secondaryAction?.invoke()
        if (side.loading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
    }
    when {
        side.error != null -> Text(
            "Errore: ${side.error}",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )

        side.deck != null -> {
            Text(
                "Formato: ${side.deck.format.name}  •  ${side.deck.totalCards}/30",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            // lista compatta, non usiamo LazyColumn qui perché siamo dentro uno scroll verticale
            side.resolved.forEach { CardRow(it) }
        }
    }
}

@Composable
private fun CardRow(rc: ResolvedCard) {
    val name = rc.card?.name ?: "dbfId ${rc.dbfId}"
    val cost = rc.card?.cost
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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
            .size(24.dp)
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
