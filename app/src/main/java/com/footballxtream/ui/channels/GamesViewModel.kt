package com.footballxtream.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.footballxtream.data.scores.EspnScoreboard
import com.footballxtream.model.Game
import com.footballxtream.model.GameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GamesViewModel : ViewModel() {
    private val api = EspnScoreboard()
    private val _games = MutableStateFlow<List<Game>>(emptyList())
    val games: StateFlow<List<Game>> = _games.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                val fetched = api.fetch()
                if (fetched.isNotEmpty()) _games.value = select(fetched)
                delay(nextDelay(_games.value))
            }
        }
    }

    private fun select(all: List<Game>): List<Game> {
        val now = System.currentTimeMillis()
        return all.filter {
            when (it.state) {
                GameState.LIVE -> true
                GameState.UPCOMING -> it.startMillis - now < 36 * 3_600_000L
                GameState.FINAL -> now - it.startMillis < 8 * 3_600_000L
            }
        }.sortedWith(compareBy({ it.state.ordinal }, { it.startMillis })).take(12)
    }

    private fun nextDelay(games: List<Game>): Long {
        val now = System.currentTimeMillis()
        return when {
            games.any { it.state == GameState.LIVE } -> 20_000L
            games.any { it.state == GameState.UPCOMING && it.startMillis - now < 30 * 60_000L } -> 30_000L
            else -> 300_000L
        }
    }
}
