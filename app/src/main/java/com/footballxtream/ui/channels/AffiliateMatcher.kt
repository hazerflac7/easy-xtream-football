package com.footballxtream.ui.channels

import android.util.Log
import com.footballxtream.model.ChannelGroup

/**
 * NFL games on ABC / CBS / NBC / FOX: find the local-affiliate channels in the playlist.
 * Order of results: home team's market, then away team's market, then (only for nationally
 * televised games, when [nationalGame] is true) any affiliate of that network.
 *
 * Handles channel names like:
 *   USA - ABC 7 LOS ANGELES CA (KABC)
 *   USA - NBC 9 DENVER HD (KUSA)
 *   USA - NBC 36 (WCNC) CHARLOTTE
 *   USA - FOX 19 NEWPORT KY (WXIX)
 * Ports the logic of the tested nfl_affiliates.py. Not compiled or run on-device yet.
 */
object AffiliateMatcher {
    private const val TAG = "GameMatch"

    private val networks = listOf("ABC", "CBS", "NBC", "FOX")

    private val usStates = setOf(
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FL", "GA", "HI", "ID", "IL", "IN",
        "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH",
        "NJ", "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT",
        "VT", "VA", "WA", "WV", "WI", "WY",
    )

    private class Market(cities: List<String>, val states: Set<String>) {
        val patterns: List<Regex> = cities.map { Regex("""\b${Regex.escape(it)}\b""") }
    }

    // ESPN team abbreviation -> city words seen in channel names, allowed state codes.
    private val markets: Map<String, Market> = mapOf(
        "BUF" to Market(listOf("BUFFALO"), setOf("NY")),
        "MIA" to Market(listOf("MIAMI", "FORT LAUDERDALE"), setOf("FL")),
        "NE" to Market(listOf("BOSTON"), setOf("MA", "NH")),
        "NYJ" to Market(listOf("NEW YORK"), setOf("NY")),
        "NYG" to Market(listOf("NEW YORK"), setOf("NY")),
        "BAL" to Market(listOf("BALTIMORE"), setOf("MD")),
        "CIN" to Market(listOf("CINCINNATI", "NEWPORT"), setOf("OH", "KY")),
        "CLE" to Market(listOf("CLEVELAND"), setOf("OH")),
        "PIT" to Market(listOf("PITTSBURGH"), setOf("PA")),
        "HOU" to Market(listOf("HOUSTON"), setOf("TX")),
        "IND" to Market(listOf("INDIANAPOLIS"), setOf("IN")),
        "JAX" to Market(listOf("JACKSONVILLE"), setOf("FL")),
        "TEN" to Market(listOf("NASHVILLE"), setOf("TN")),
        "DEN" to Market(listOf("DENVER"), setOf("CO")),
        "KC" to Market(listOf("KANSAS CITY"), setOf("MO", "KS")),
        "LV" to Market(listOf("LAS VEGAS"), setOf("NV")),
        "LAC" to Market(listOf("LOS ANGELES"), setOf("CA")),
        "LAR" to Market(listOf("LOS ANGELES"), setOf("CA")),
        "DAL" to Market(listOf("DALLAS", "FORT WORTH"), setOf("TX")),
        "PHI" to Market(listOf("PHILADELPHIA"), setOf("PA")),
        "WSH" to Market(listOf("WASHINGTON"), setOf("DC")),
        "CHI" to Market(listOf("CHICAGO"), setOf("IL")),
        "DET" to Market(listOf("DETROIT"), setOf("MI")),
        "GB" to Market(listOf("GREEN BAY", "MILWAUKEE"), setOf("WI")),
        "MIN" to Market(listOf("MINNEAPOLIS", "ST PAUL"), setOf("MN")),
        "ATL" to Market(listOf("ATLANTA"), setOf("GA")),
        "CAR" to Market(listOf("CHARLOTTE"), setOf("NC")),
        "NO" to Market(listOf("NEW ORLEANS"), setOf("LA")),
        "TB" to Market(listOf("TAMPA", "ST PETERSBURG"), setOf("FL")),
        "ARI" to Market(listOf("PHOENIX"), setOf("AZ")),
        "SF" to Market(listOf("SAN FRANCISCO", "OAKLAND", "SAN JOSE"), setOf("CA")),
        "SEA" to Market(listOf("SEATTLE"), setOf("WA")),
    )

    // Abbreviation variants other sources use -> the key above.
    private val aliases = mapOf("WAS" to "WSH", "JAC" to "JAX", "LVR" to "LV", "LA" to "LAR")

    private val spaces = Regex("""\s+""")

    // Quality tags must not be read as a state code or city word. (SD is also South Dakota.)
    private val quality = Regex("""\b(?:HD|FHD|UHD|4K)\b""")

    // National / non-affiliate feeds that must never be picked as a local station.
    private val exclude = Regex(
        """\b(?:ABC|CBS|NBC|FOX)\s+(?:NEWS|SOUL|SPORTS|WEATHER|BUSINESS|DEPORTES)\b""",
    )

    private const val CALL = """\(([A-Z0-9]{3,6})(?:-[A-Z]{1,2})?\)"""

    // NET [num] CITY [ST] (CALL)   -> groups: 1 net, 2 num, 3 place, 4 call
    private val nameRe = Regex("""\b(ABC|CBS|NBC|FOX)\b\s*(\d+)?\s+(.*?)\s*$CALL\s*$""")

    // NET [num] (CALL) CITY [ST]   -> groups: 1 net, 2 num, 3 call, 4 place
    private val altRe = Regex("""\b(ABC|CBS|NBC|FOX)\b\s*(\d+)?\s*$CALL\s*(.*)$""")

    private class Affiliate(val channel: ChannelGroup, val city: String, val state: String)

    /** Network named in ESPN's broadcast list (e.g. ["ESPN", "ABC"]), or null if none of the four. */
    fun networkOf(broadcasts: List<String>): String? {
        val text = broadcasts.joinToString(" ").uppercase()
        return networks.firstOrNull { Regex("""\b$it\b""").containsMatchIn(text) }
    }

    fun match(
        all: List<ChannelGroup>,
        homeAbbr: String,
        awayAbbr: String,
        broadcasts: List<String>,
        nationalGame: Boolean = false,
    ): List<ChannelGroup> {
        val network = networkOf(broadcasts) ?: return emptyList()
        val home = markets[aliases[homeAbbr] ?: homeAbbr] ?: return emptyList()
        val away = markets[aliases[awayAbbr] ?: awayAbbr]

        val pool = all.mapNotNull { parse(it, network) }
        if (pool.isEmpty()) return emptyList()

        val homeHits = pool.filter { inMarket(it, home) }
        val awayHits: List<Affiliate> =
            if (away == null) emptyList() else pool.filter { inMarket(it, away) }
        Log.d(
            TAG,
            "$awayAbbr@$homeAbbr on $network: home=${homeHits.size} away=${awayHits.size} pool=${pool.size}",
        )

        val ordered: List<Affiliate> = homeHits + awayHits + (if (nationalGame) pool else emptyList())
        return ordered.map { it.channel }.distinctBy { it.key }
    }

    private fun clean(raw: String): String =
        spaces.replace(quality.replace(raw.replace(".", "").uppercase(), " "), " ").trim()

    private fun splitState(place: String): Pair<String, String> {
        val tokens = place.trim().split(" ").filter { it.isNotEmpty() }
        if (tokens.size > 1 && tokens.last() in usStates) {
            return tokens.dropLast(1).joinToString(" ") to tokens.last()
        }
        return place.trim() to ""
    }

    private fun parse(channel: ChannelGroup, network: String): Affiliate? {
        val name = clean(channel.displayName)
        if (!name.contains(network) || exclude.containsMatchIn(name)) return null

        val a = nameRe.find(name)
        val b = if (a == null) altRe.find(name) else null
        val (net, place) = when {
            a != null -> a.groupValues[1] to a.groupValues[3]
            b != null -> b.groupValues[1] to b.groupValues[4]
            else -> return null
        }
        if (net != network) return null

        val (city, state) = splitState(place)
        return Affiliate(channel, city, state)
    }

    private fun inMarket(a: Affiliate, m: Market): Boolean {
        val cityOk = m.patterns.any { it.containsMatchIn(a.city) }
        return cityOk && (a.state.isEmpty() || a.state in m.states)
    }
}
