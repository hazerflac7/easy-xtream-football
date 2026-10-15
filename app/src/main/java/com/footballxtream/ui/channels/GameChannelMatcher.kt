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
    private const val MIN_SCORE = 20

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
    private val whitespaceRegex = Regex("\\s+")

    suspend fun match(
        repository: ContentRepository,
        folders: List<ChannelFolder>,
        game: Game,
        clock: () -> Long = { System.currentTimeMillis() },
    ): List<ChannelGroup> {
        val all = folders.flatMap { it.channels }.distinctBy { it.key }
        val now = clock()

        val scoredByName = all.mapNotNull { ch ->
            val score = scoreMatch(ch.displayName, game)
            if (score >= MIN_SCORE) {
                val t = eventStartMillis(ch.displayName, now)
                if (t != null) {
                    val diff = abs(t - game.startMillis)
                    if (diff <= TIME_TOLERANCE_MS) Triple(ch, score, diff) else null
                } else {
                    if (game.state == GameState.FINAL) null else Triple(ch, score, Long.MAX_VALUE)
                }
            } else {
                null
            }
        }.sortedWith(compareBy<Triple<ChannelGroup, Int, Long>> { it.third }.thenByDescending { it.second }).map { it.first }

        if (scoredByName.isNotEmpty()) {
            Log.d(TAG, "${game.awayAbbr}@${game.homeAbbr}: ${scoredByName.size} by name")
            return scoredByName.take(MAX_RESULTS)
        }

        var slotHits: List<ChannelGroup> = emptyList()
        // Blank numbered NFL slots ("NFL | 01 -") carry the current prime-time game.
        val blankSlot = Regex("""^NFL\s*\|\s*\d{1,2}\s*-?\s*$""", RegexOption.IGNORE_CASE)
        val isFootball = game.league.contains("nfl", ignoreCase = true) ||
            game.league.contains("football", ignoreCase = true)
        val startsSoon = game.startMillis - now <= 60 * 60_000L

        // NFL on ABC/CBS/NBC/FOX: local-affiliate channels in the home, then away, market.
        val affiliateHits: List<ChannelGroup> = if (isFootball && game.state != GameState.FINAL) {
            AffiliateMatcher.match(
                all, game.homeAbbr, game.awayAbbr, game.broadcasts,
                nationalGame = isPrimeTime(game.startMillis),
            )
        } else {
            emptyList()
        }
        Log.d(TAG, "${game.awayAbbr}@${game.homeAbbr}: broadcasts=${game.broadcasts} affiliates=${affiliateHits.size}")
        if (affiliateHits.isNotEmpty()) return affiliateHits.take(MAX_RESULTS)
        if (isFootball && game.state != GameState.FINAL && (game.state == GameState.LIVE || startsSoon)) {
            val slots = all.filter { blankSlot.matches(it.displayName.trim()) }
            if (slots.isNotEmpty()) {
                Log.d(TAG, "${game.awayAbbr}@${game.homeAbbr}: ${slots.size} blank slots")
                slotHits = slots.take(MAX_RESULTS)
            }
        }

        val winStart = game.startMillis - 30 * 60_000L
        val winEnd = game.startMillis + 4 * 3_600_000L
        val candidates = all
            .sortedByDescending { cityScore(it.displayName, game) * 2 + if (hint.containsMatchIn(it.displayName.lowercase())) 1 else 0 }
            .take(MAX_EPG_CANDIDATES)

        val byEpg = withTimeoutOrNull(TIMEOUT_MS) {
            candidates.chunked(CONCURRENCY).flatMap { chunk ->
                coroutineScope {
                    chunk.map { g ->
                        async(Dispatchers.IO) {
                            val programs = runCatching { repository.epgFor(g) }.getOrDefault(emptyList())
                            val activePrograms = programs.filter { p ->
                                val actualEnd = if (p.end == 0L) p.start + 2 * 3_600_000L else p.end
                                // allow matches that end after now (not expired), and fall within the game window
                                // we shouldn't enforce actualEnd > winStart since actualEnd > now already ensures it hasn't expired.
                                // the start condition just ensures it doesn't start way too late (after the game ends).
                                actualEnd > now && p.start < winEnd
                            }

                            val bestScore = activePrograms.maxOfOrNull { p -> scoreMatch(p.title, game) } ?: 0
                            if (bestScore >= MIN_SCORE) {
                                Pair(g, bestScore)
                            } else {
                                null
                            }
                        }
                    }.awaitAll()
                }
            }.filterNotNull()
        } ?: emptyList()

        Log.d(TAG, "${game.awayAbbr}@${game.homeAbbr}: 0 by name, ${candidates.size} epg candidates, ${byEpg.size} epg hits")
        val epgHits = byEpg.sortedByDescending { it.second }.map { it.first }
        return (slotHits + epgHits).distinctBy { it.key }.take(MAX_RESULTS)
    }

    private fun scoreMatch(text: String, game: Game): Int {
        val normText = norm(text)
        val textTokens = text.lowercase().replace(nonAlnum, " ").trim().split(whitespaceRegex).filter { it.isNotEmpty() }
        val awayScore = scoreTeam(normText, textTokens, game.awayName, game.awayAbbr)
        val homeScore = scoreTeam(normText, textTokens, game.homeName, game.homeAbbr)

        if (awayScore > 0 && homeScore > 0) {
            return awayScore + homeScore
        }
        return 0
    }

    private fun scoreTeam(normText: String, tokens: List<String>, name: String, abbr: String): Int {
        val nameTokens = name.lowercase().replace(nonAlnum, " ").trim().split(whitespaceRegex).filter { it.isNotEmpty() }
        var score = 0

        // Exact full name match
        if (normText.contains(norm(name))) {
            score += 20
        }

        // Parts of name match (like city or mascot)
        for (token in nameTokens) {
            if (token.length >= 4 && normText.contains(norm(token))) {
                score += 10
            }
        }

        // Abbreviation match (as isolated token)
        if (abbr.isNotEmpty()) {
            val lowerAbbr = abbr.lowercase()
            if (tokens.contains(lowerAbbr)) {
                score += 15
            }
        }

        return score
    }

    /** True for kickoffs at 7pm Eastern or later: Sunday/Monday/Thursday night games air nationwide. */
    private fun isPrimeTime(startMillis: Long): Boolean {
        val cal = Calendar.getInstance(PROVIDER_ZONE)
        cal.timeInMillis = startMillis
        return cal.get(Calendar.HOUR_OF_DAY) >= 19
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

    private fun cityScore(name: String, game: Game): Int {
        val t = norm(name)
        var s = 0
        for (full in listOf(game.awayName, game.homeName)) {
            val words = full.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.size >= 2) {
                val city = norm(words.dropLast(1).joinToString(" "))
                val first = norm(words.first())
                if (t.contains(city) || (first.trim().length >= 5 && t.contains(first))) s += 2
            }
        }
        return s
    }

    private fun norm(s: String) = " " + s.lowercase().replace(nonAlnum, " ").trim() + " "


}
