package com.footballxtream.model

enum class GameState { LIVE, UPCOMING, FINAL }

data class Game(
    val id: String,
    val league: String,
    val startMillis: Long,
    val state: GameState,
    val statusText: String,
    val awayName: String,
    val awayAbbr: String,
    val awayLogo: String?,
    val awayScore: Int?,
    val homeName: String,
    val homeAbbr: String,
    val homeLogo: String?,
    val homeScore: Int?,
)
