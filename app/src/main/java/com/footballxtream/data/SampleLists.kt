package com.footballxtream.data

import com.footballxtream.data.local.ProfileDao
import com.footballxtream.data.local.ProfileEntity
import com.footballxtream.data.local.ProfileType
import com.footballxtream.data.local.Secret

/**
 * The sample playlist offered when there is no profile yet ("Try with a sample radio playlist").
 * No stream is bundled or hosted by the app: the list only points at the stations' own public
 * streams, and is added on the user's request as an ordinary M3U profile that can be edited or
 * deleted like any other.
 *
 * Radio only, on purpose: the community list of "free-to-air" sports TV offered in 0.1.8 turned out
 * to carry pay-TV channels and got that version rejected from a store. Don't add third-party
 * lists here; anything offered must be checked stream by stream.
 */
object SampleLists {
    /** Sports talk radio, official public streams of the stations; kept in this project's repo. */
    const val SPORTS_RADIO_URL =
        "https://raw.githubusercontent.com/nezor11/easy-xtream-football/main/docs/playlists/sports-radio.m3u"

    /** Inserts the sample profile with the given (localized) name. */
    suspend fun add(profileDao: ProfileDao, radioName: String) {
        profileDao.upsert(ProfileEntity(name = radioName, type = ProfileType.M3U, m3uUrl = Secret(SPORTS_RADIO_URL)))
    }
}
