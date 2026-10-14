package com.footballxtream.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.footballxtream.model.Game
import com.footballxtream.model.GameState
import kotlinx.coroutines.delay
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage

@Composable
fun GamesRow(modifier: Modifier = Modifier, onGameClick: (Game) -> Unit = {}, viewModel: GamesViewModel = viewModel()) {
    val games by viewModel.games.collectAsState()
    if (games.isEmpty()) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    Column(modifier) {
        Text(
            "Games",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(games, key = { it.id }) { g -> GameCard(g, now, onClick = { onGameClick(g) }) }
        }
    }
}

@Composable
private fun GameCard(game: Game, now: Long, onClick: () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val colors = MaterialTheme.colorScheme
    val untilStart = game.startMillis - now

    val (badge, badgeColor) = when {
        game.state == GameState.LIVE ->
            "LIVE" + (if (game.statusText.isNotBlank()) " · ${game.statusText}" else "") to Color(0xFFE53935)
        game.state == GameState.FINAL -> "FINAL" to colors.onSurfaceVariant
        untilStart <= 0L -> "STARTING NOW" to Color(0xFFFFA000)
        untilStart <= 30 * 60_000L -> "STARTING SOON · ${countdown(untilStart)}" to Color(0xFFFFA000)
        else -> "In ${countdown(untilStart)}" to colors.onSurfaceVariant
    }
    val kickoff = java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(game.startMillis))

    Column(
        modifier = Modifier
            .width(250.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(2.dp, if (focused) colors.onBackground else Color.Transparent, shape)
            .clip(shape)
            .background(colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(badge, color = badgeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TeamBlock(game.awayLogo, game.awayAbbr)
            val mid = if (game.state == GameState.UPCOMING) "@"
            else "${game.awayScore ?: "-"} - ${game.homeScore ?: "-"}"
            Text(mid, color = colors.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            TeamBlock(game.homeLogo, game.homeAbbr)
        }
        Text(
            "${game.league} · $kickoff",
            color = colors.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun TeamBlock(logo: String?, abbr: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(model = logo, contentDescription = abbr, modifier = Modifier.size(56.dp))
        Text(abbr, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
    }
}

private fun countdown(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val d = s / 86_400
    val h = (s % 86_400) / 3_600
    val m = (s % 3_600) / 60
    val sec = s % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "%d:%02d".format(m, sec)
    }
}
