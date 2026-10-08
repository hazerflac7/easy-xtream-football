package com.footballxtream.data.scores

import com.footballxtream.model.Game
import com.footballxtream.model.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable private data class EspnResponse(val events: List<EspnEvent> = emptyList())
@Serializable private data class EspnEvent(
    val id: String,
    val date: String,
    val status: EspnStatus,
    val competitions: List<EspnCompetition> = emptyList(),
)
@Serializable private data class EspnStatus(val type: EspnStatusType)
@Serializable private data class EspnStatusType(val state: String = "pre", val shortDetail: String? = null)
@Serializable private data class EspnCompetition(val competitors: List<EspnCompetitor> = emptyList())
@Serializable private data class EspnCompetitor(
    val homeAway: String,
    val score: String? = null,
    val team: EspnTeam,
)
@Serializable private data class EspnTeam(
    val displayName: String = "",
    val abbreviation: String = "",
    val logo: String? = null,
)

/** Reads ESPN's public (unofficial) scoreboard JSON. Failures return an empty list. */
class EspnScoreboard(private val client: OkHttpClient = OkHttpClient()) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetch(): List<Game> = withContext(Dispatchers.IO) {
        LEAGUES.flatMap { (label, path) ->
            runCatching { league(label, path) }.getOrDefault(emptyList())
        }
    }

    private fun league(label: String, path: String): List<Game> {
        val req = Request.Builder()
            .url("https://site.api.espn.com/apis/site/v2/sports/$path/scoreboard")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string() ?: return emptyList()
            return json.decodeFromString(EspnResponse.serializer(), body)
                .events.mapNotNull { toGame(label, it) }
        }
    }

    private fun toGame(label: String, e: EspnEvent): Game? {
        val comp = e.competitions.firstOrNull() ?: return null
        val home = comp.competitors.firstOrNull { it.homeAway == "home" } ?: return null
        val away = comp.competitors.firstOrNull { it.homeAway == "away" } ?: return null
        val start = parseDate(e.date) ?: return null
        val state = when (e.status.type.state) {
            "in" -> GameState.LIVE
            "post" -> GameState.FINAL
            else -> GameState.UPCOMING
        }
        return Game(
            id = "$label-${e.id}", league = label, startMillis = start, state = state,
            statusText = e.status.type.shortDetail.orEmpty(),
            awayName = away.team.displayName, awayAbbr = away.team.abbreviation,
            awayLogo = away.team.logo, awayScore = away.score?.toIntOrNull(),
            homeName = home.team.displayName, homeAbbr = home.team.abbreviation,
            homeLogo = home.team.logo, homeScore = home.score?.toIntOrNull(),
        )
    }

    private fun parseDate(s: String): Long? {
        for (p in listOf("yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")) {
            try {
                val f = SimpleDateFormat(p, Locale.US)
                f.timeZone = TimeZone.getTimeZone("UTC")
                return f.parse(s)?.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    companion object {
        val LEAGUES = listOf("NFL" to "football/nfl", "NBA" to "basketball/nba")
    }
}
