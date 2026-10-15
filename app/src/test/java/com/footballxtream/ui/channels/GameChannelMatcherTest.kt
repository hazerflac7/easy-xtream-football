package com.footballxtream.ui.channels

import com.footballxtream.data.ContentRepository
import com.footballxtream.model.ChannelFolder
import com.footballxtream.model.ChannelGroup
import com.footballxtream.model.ChannelVariant
import com.footballxtream.model.EpgProgram
import com.footballxtream.model.Game
import com.footballxtream.model.GameState
import com.footballxtream.model.LiveChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class GameChannelMatcherTest {

    @Test
    fun match_epgFiltersExpiredShows() = runBlocking {
        val now = System.currentTimeMillis()

        // Setup channels
        val ch1 = ChannelGroup(key = "c1", displayName = "Sport 1", variants = listOf(variant("c1")), iconUrl = null, isFootball = true, epgId = null, country = null, geoBlocked = false, isRadio = false)
        val ch2 = ChannelGroup(key = "c2", displayName = "Sport 2", variants = listOf(variant("c2")), iconUrl = null, isFootball = true, epgId = null, country = null, geoBlocked = false, isRadio = false)
        val ch3 = ChannelGroup(key = "c3", displayName = "Sport 3", variants = listOf(variant("c3")), iconUrl = null, isFootball = true, epgId = null, country = null, geoBlocked = false, isRadio = false)
        val ch4 = ChannelGroup(key = "c4", displayName = "Sport 4", variants = listOf(variant("c4")), iconUrl = null, isFootball = true, epgId = null, country = null, geoBlocked = false, isRadio = false)
        val ch5 = ChannelGroup(key = "c5", displayName = "Sport 5", variants = listOf(variant("c5")), iconUrl = null, isFootball = true, epgId = null, country = null, geoBlocked = false, isRadio = false)
        val folders = listOf(ChannelFolder(name = "F", iconUrl = null, isFootball = true, channels = listOf(ch1, ch2, ch3, ch4, ch5)))

        // Game matches EPG titles
        val game = Game(
            id = "g1",
            league = "NFL",
            awayName = "Dallas Cowboys",
            awayAbbr = "DAL",
            homeName = "Philadelphia Eagles",
            homeAbbr = "PHI",
            startMillis = now,
            state = GameState.LIVE,
            statusText = "LIVE",
            awayLogo = null,
            awayScore = null,
            homeLogo = null,
            homeScore = null,
            broadcasts = emptyList()
        )

        // ch1 has an active matching show
        val activeShow = EpgProgram(title = "Cowboys @ Eagles", start = now - 10000, end = now + 10000, nowFlag = false)

        // ch2 has an expired matching show
        val expiredShow = EpgProgram(title = "Cowboys @ Eagles (Replay)", start = now - 20000, end = now - 10000, nowFlag = false)

        // ch3 has a future matching show (starts in 10s)
        val futureShow = EpgProgram(title = "Cowboys @ Eagles (Replay)", start = now + 10000, end = now + 20000, nowFlag = false)

        // ch4 has an active matching show but no end time (simulating stop == 0L)
        // Set start back by 1 hour (less than the default 2 hours window) so it stays active
        val activeShowNoStop = EpgProgram(title = "Cowboys @ Eagles", start = now - 3600_000L, end = 0L, nowFlag = false)

        // ch5 has an expired matching show but no end time (simulating stop == 0L)
        // start + 2 hours < now => expired
        val expiredShowNoStop = EpgProgram(title = "Cowboys @ Eagles", start = now - 3 * 3_600_000L, end = 0L, nowFlag = false)

        val mockEpgs = mapOf("c1" to listOf(activeShow), "c2" to listOf(expiredShow), "c3" to listOf(futureShow), "c4" to listOf(activeShowNoStop), "c5" to listOf(expiredShowNoStop))

        val repository = object : ContentRepository(File(""), object : com.footballxtream.data.LogoRepository(File("")) {}) {
            override suspend fun epgFor(group: ChannelGroup): List<EpgProgram> {
                return mockEpgs[group.key] ?: emptyList()
            }
        }

        val hits = GameChannelMatcher.match(repository, folders, game, clock = { now })

        val hitKeys = hits.map { it.key }.toSet()
        // c3 represents a future show that falls in the window, which is valid if its start < winEnd
        assertEquals(setOf("c1", "c3", "c4"), hitKeys)
    }

    @Test
    fun match_fuzzyMatchesStreamName() = runBlocking {
        val now = System.currentTimeMillis()

        // Setup channels with messy names
        val ch1 = ChannelGroup(key = "c1", displayName = "ESPN | LAL @ BOS", variants = listOf(variant("c1")), iconUrl = null, isFootball = false, epgId = null, country = null, geoBlocked = false, isRadio = false)
        val ch2 = ChannelGroup(key = "c2", displayName = "SPORTS | Boston Celtics vs Los Angeles Lakers", variants = listOf(variant("c2")), iconUrl = null, isFootball = false, epgId = null, country = null, geoBlocked = false, isRadio = false)
        val ch3 = ChannelGroup(key = "c3", displayName = "Random Stream", variants = listOf(variant("c3")), iconUrl = null, isFootball = false, epgId = null, country = null, geoBlocked = false, isRadio = false)

        val folders = listOf(ChannelFolder(name = "NBA", iconUrl = null, isFootball = false, channels = listOf(ch1, ch2, ch3)))

        val game = Game(
            id = "g1",
            league = "NBA",
            awayName = "Los Angeles Lakers",
            awayAbbr = "LAL",
            homeName = "Boston Celtics",
            homeAbbr = "BOS",
            startMillis = now + 10000,
            state = GameState.LIVE, // PREGAME isn't defined, fallback to LIVE which makes sense
            statusText = "PREGAME",
            awayLogo = null,
            awayScore = null,
            homeLogo = null,
            homeScore = null,
            broadcasts = emptyList()
        )

        val repository = object : ContentRepository(File(""), object : com.footballxtream.data.LogoRepository(File("")) {}) {
            override suspend fun epgFor(group: ChannelGroup): List<EpgProgram> {
                return emptyList()
            }
        }

        val hits = GameChannelMatcher.match(repository, folders, game, clock = { now })

        val hitKeys = hits.map { it.key }.toSet()
        // Both c1 (abbreviations) and c2 (full names) should match.
        assertEquals(setOf("c1", "c2"), hitKeys)
    }

    private fun variant(id: String) = ChannelVariant(
        channel = LiveChannel(streamId = 1, name = id, categoryName = "Cats", streamUrl = "http://url", iconUrl = null, epgId = id, geoBlocked = false, isRadio = false, country = null),
        quality = com.footballxtream.model.Quality.UNKNOWN
    )
}
