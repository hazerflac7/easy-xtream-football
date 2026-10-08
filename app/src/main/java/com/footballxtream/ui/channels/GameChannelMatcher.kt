package com.footballxtream.ui.channels

import android.util.Log
import com.footballxtream.data.ContentRepository
import com.footballxtream.model.ChannelFolder
import com.footballxtream.model.ChannelGroup
import com.footballxtream.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Finds playlist channels likely carrying [Game]:
 * 1) channels whose own name mentions both teams (event feeds),
 * 2) otherwise channels whose guide has a programme naming both teams around kickoff.
 */
object GameChannelMatcher {
    private const val TAG = "GameMatch"
    private const val MAX_EPG_CANDIDATES = 150
    private const val CONCURRENCY = 10
    private const val MAX_RESULTS = 8
    private const val TIMEOUT_MS = 15_000L

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

        val byName = all.filter { mentions(it.displayName, away) && mentions(it.displayName, home) }
        if (byName.isNotEmpty()) {
            Log.d(TAG, "${game.awayAbbr}@${game.homeAbbr}: ${byName.size} by name")
            return byName.take(MAX_RESULTS)
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
