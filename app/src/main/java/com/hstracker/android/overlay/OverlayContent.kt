package com.hstracker.android.overlay

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hstracker.android.game.GameSession
import com.hstracker.android.game.GameState
import com.hstracker.android.game.TrackedCard

/**
 * Root dell'overlay: due sezioni indipendenti — il tuo mazzo e quello (probabile)
 * dell'avversario. Ognuna col suo pallino colorato, contatore, e tap-per-pescata.
 */
@Composable
fun OverlayRoot() {
    val player by GameSession.player.collectAsStateWithLifecycle()
    val opponent by GameSession.opponent.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (player == null && opponent == null) {
        MinimalBadge("Nessuna partita")
        return
    }

    Column(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 260.dp)
            .background(Color(0xCC101418), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(10.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp),
    ) {
        // Header con "×" per chiudere l'intero overlay
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "HSTracker",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "×",
                color = Color(0xFFFF8A80),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clickable { stopOverlay(context) },
            )
        }
        Spacer(Modifier.height(4.dp))

        player?.let {
            DeckSection(
                title = "Il mio mazzo",
                accent = Color(0xFFB39DDB),
                state = it,
                onTap = GameSession::onPlayerCardDrawn,
                onUndo = GameSession::onPlayerUndoDraw,
            )
        }

        if (player != null && opponent != null) Spacer(Modifier.height(6.dp))

        opponent?.let {
            DeckSection(
                title = "Avversario",
                accent = Color(0xFFEF9A9A),
                state = it,
                onTap = GameSession::onOpponentCardSeen,
                onUndo = GameSession::onOpponentUndoSeen,
            )
        }
    }
}

@Composable
private fun DeckSection(
    title: String,
    accent: Color,
    state: GameState,
    onTap: (Int) -> Unit,
    onUndo: (Int) -> Unit,
) {
    var collapsed by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { collapsed = !collapsed },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallDot(accent)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$title  ${state.remainingTotal}/${state.remainingTotal + state.playedTotal}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (collapsed) "▸" else "▾",
                color = Color.White,
                fontSize = 12.sp,
            )
        }
        if (!collapsed) {
            Spacer(Modifier.height(3.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.height(cardListHeight(state.cards.size)),
            ) {
                items(state.cards, key = { it.dbfId }) { card ->
                    CardRow(card, onTap = onTap, onUndo = onUndo)
                }
            }
        }
    }
}

/** Altezza dinamica: piccola con 15 carte come cap, poi scroll interno. */
private fun cardListHeight(count: Int): androidx.compose.ui.unit.Dp {
    val perRow = 20 // dp per riga circa
    val cap = 15
    val rows = kotlin.math.min(count, cap)
    return (rows * perRow).dp
}

@Composable
private fun CardRow(
    card: TrackedCard,
    onTap: (Int) -> Unit,
    onUndo: (Int) -> Unit,
) {
    val faded = card.remaining == 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (faded) Color(0x22FFFFFF) else Color(0x11FFFFFF),
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable(enabled = card.remaining > 0) { onTap(card.dbfId) },
    ) {
        ManaGem(card.cost, faded)
        Text(
            text = card.name,
            color = if (faded) Color(0x88FFFFFF) else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${card.remaining}",
            color = if (faded) Color(0x66FFFFFF) else Color(0xFFFFD54F),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "+",
            color = Color(0x99FFFFFF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(start = 2.dp)
                .clickable(enabled = card.remaining < card.initial) { onUndo(card.dbfId) },
        )
    }
}

@Composable
private fun ManaGem(cost: Int?, faded: Boolean) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(
                if (faded) Color(0xFF37474F) else Color(0xFF1E88E5),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cost?.toString() ?: "?",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SmallDot(color: Color) {
    Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
}

@Composable
private fun MinimalBadge(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xCC101418), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text, color = Color.White, fontSize = 12.sp)
    }
}

private fun stopOverlay(context: Context) {
    context.startService(
        Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_STOP)
    )
}
