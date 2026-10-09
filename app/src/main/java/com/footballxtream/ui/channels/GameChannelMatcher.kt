package com.footballxtream.ui.channels

import android.util.Log
import com.footballxtream.data.ContentRepository
import com.footballxtream.model.ChannelFolder
import com.footballxtream.model.ChannelGroup
import com.footballxtream.model.Game
import com.footballxtream.model.GameState
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Finds playlist channels likely carrying [Game]:
 * 1) event channels whose name mentions both teams AND (when the name carries a start time)
 *    whose start time is close to kickoff, best time match first,
 * 2) otherwise channels whose guide has a programme naming both teams around kickoff.
 */
object GameChannelMatcher {
    private const val TAG = "GameMatch"
    private const val MAX_EPG_CANDIDATES = 150
    private const val CONCURRENCY = 10
    private const val MAX_RESULTS = 8
    private const val TIMEOUT_MS = 15_000L

    // Provider writes times like "@ Oct 8 7:00 PM". Assumed US Eastern; check the GameMatch log.
    private val PROVIDER_ZONE = TimeZone.getTimeZone("America/New_York")
    private const val TIME_TOLERANCE_MS = 90 * 60_000L
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val eventTime = Regex(
        """@\s*([A-Za-z]{3})\s+(\d{1,2})\s+(\d{1,2}):(\d{2})\s*([AaPp][Mm])""",
    )

    private val nonAlnum = Regex("[^a-z0-9]+")
    private val hint = Regex(
        "nfl|nba|espn|fox|cbs|nbc|abc|tnt|tbs|prime|peacock|sportsnet|fanduel|altitude|msg|yes|network|tsn|bally|spectrum|sports",
    )

    suspend fun match(
        repository: ContentRepository,
        folders: List<ChannelFolder>,
        game: Game,
    ): List<ChannelGroup> {
        val all = folders.flatMap { it.channels }.distinctBy { it.key }
        val away = teamTerms(game.awayName)
        val home = teamTerms(game.homeName)
        val now = System.currentTimeMillis()

        val nameHits = all.filter { mentions(it.displayName, away) && mentions(it.displayName, home) }
        val scored = nameHits.mapNotNull { ch ->
            val t = eventStartMillis(ch.displayName, now)
            if (t != null) {
                val diff = abs(t - game.startMillis)
                Log.d(TAG, "${ch.displayName}: name time=$t game=${game.startMillis} diffMin=${diff / 60_000}")
                if (diff <= TIME_TOLERANCE_MS) ch to diff else null
            } else {
                // No time in the name: can't verify, so skip it for finished games, rank it last otherwise.
                if (game.state == GameState.FINAL) null else ch to Long.MAX_VALUE
            }
        }.sortedBy { it.second }.map { it.first }

        if (scored.isNotEmpty()) {
            Log.d(TAG, "${game.awayAbbr}@${game.homeAbbr}: ${scored.size} by name (${nameHits.size} before time check)")
            return scored.take(MAX_RESULTS)
        }

        // Tier 2: team-market feeds, then national NFL feeds.
        val eventListing = Regex("""\bvs\b|\bat\b|@""", RegexOption.IGNORE_CASE)
        val marketTag = Regex("""\b(cbs|fox|nbc|abc)\b""", RegexOption.IGNORE_CASE)
        val nationalTag = Regex("""nfl network|nfl redzone|prime video|amazon|netflix""", RegexOption.IGNORE_CASE)
        if (game.state != GameState.FINAL) {
            val marketHits = all.filter { ch ->
                val n = ch.displayName
                (mentions(n, away) != mentions(n, home)) &&
                    marketTag.containsMatchIn(n) && !eventListing.containsMatchIn(n)
            }
            val nationalHits = if (game.league.contains("nfl", ignoreCase = true)) {
                all.filter { ch ->
                    val n = ch.displayName
                    nationalTag.containsMatchIn(n) && !eventListing.containsMatchIn(n) &&
                        !mentions(n, away) && !mentions(n, home)
                }
            } else emptyList()
            val tier2 = (marketHits + nationalHits).distinctBy { it.key }
            if (tier2.isNotEmpty()) {
                Log.d(TAG, "${game.awayAbbr}@${game.homeAbbr}: ${marketHits.size} market + ${nationalHits.size} national")
                return tier2.take(MAX_RESULTS)
            }
        }

        val winStart = game.startMillis - 30 * 60_000L
        val winEnd = game.startMillis + 4 * 3_600_000L
        val candidates = all
            .sortedByDescending { if (hint.containsMatchIn(it.displayName.lowercase())) 1 else 0 }
            .take(MAX_EPG_CANDIDATES)

        val byEpg = withTimeoutOrNull(TIMEOUT_MS) {
            candidates.chunked(CONCURRENCY).flatMap { chunk ->
                coroutineScope {
                    chunk.map { g ->
                        async(Dispatchers.IO) {
                            val hit = runCatching { repository.epgFor(g) }.getOrDefault(emptyList())
                                .any { p ->
                                    p.end > winStart && p.start < winEnd &&
                                        mentions(p.title, away) && mentions(p.title, home)
                                }
                            if (hit) g else null
                        }
                    }.awaitAll()
                }
            }.filterNotNull()
        } ?: emptyList()

        Log.d(TAG, "${game.awayAbbr}@${game.homeAbbr}: 0 by name, ${candidates.size} epg candidates, ${byEpg.size} epg hits")
        return byEpg.take(MAX_RESULTS)
    }

    /** Parses "@ Oct 8 7:00 PM" from a channel name into epoch millis, or null if absent. */
    private fun eventStartMillis(name: String, nowMillis: Long): Long? {
        val m = eventTime.find(name) ?: return null
        val (mon, day, hh, mm, ap) = m.destructured
        val month = months.indexOf(mon.lowercase())
        if (month < 0) return null
        var hour = hh.toInt() % 12
        if (ap.equals("PM", ignoreCase = true)) hour += 12
        val cal = Calendar.getInstance(PROVIDER_ZONE)
        cal.timeInMillis = nowMillis
        val year = cal.get(Calendar.YEAR)
        cal.clear()
        cal.set(year, month, day.toInt(), hour, mm.toInt(), 0)
        var t = cal.timeInMillis
        val halfYear = 180L * 24 * 3_600_000L
        if (t < nowMillis - halfYear) { cal.set(Calendar.YEAR, year + 1); t = cal.timeInMillis }
        else if (t > nowMillis + halfYear) { cal.set(Calendar.YEAR, year - 1); t = cal.timeInMillis }
        return t
    }

    private fun norm(s: String) = " " + s.lowercase().replace(nonAlnum, " ").trim() + " "

    private fun teamTerms(displayName: String): List<String> {
        val words = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val terms = linkedSetOf(words.joinToString(" "), words.last())
        if (words.size >= 3) {
            terms += words.takeLast(2).joinToString(" ")
            terms += words.takeLast(2).joinToString("")
        }
        return terms.map { norm(it) }.filter { it.length >= 6 }.distinct()
    }

    private fun mentions(text: String, terms: List<String>): Boolean {
        val t = norm(text)
        return terms.any { t.contains(it) }
    }
}
