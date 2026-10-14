# Agent Instructions for easy-xtream-football

## Project Overview
This is an Android TV IPTV streaming application (`com.personal.tvstream`) focused on live sports. It parses M3U playlists and XMLTV EPG data to automate channel matching and populate game selection cards. 

## Tech Stack
* **Language:** Kotlin
* **Platform:** Android TV (Targeting devices like Nvidia Shield / vSeeBox)
* **UI Framework:** AndroidX Leanback
* **CI/CD:** GitHub Actions (Compiles and packages debug APKs)

## Core Architecture & Critical Context
* **Channel Matching:** Core logic for mapping streams to games resides in `GameChannelMatcher.kt`. 
  * **Critical:** Pay strict attention to EPG lookup and timestamp boundaries. The parser must strictly filter out ended games to prevent stream name false-positives.
* **Leanback UI Customization:** The app uses heavily customized Leanback layouts (e.g., `item_match_card.xml`, `bg_match_card.xml`).
  * The category sidebar is intentionally disabled (`BrowseSupportFragment.HEADERS_DISABLED`). Do not re-enable it.
  * **Theme/Styling:** Maintain the established palette: navy blue cards (`#0D1E36`, `#1A3B66`), cyan focus borders (`#00E5FF`), and prominent EPG titles (`18sp`).

## Coding Guidelines
* Use Kotlin Coroutines/Flow for asynchronous parsing of large M3U and XMLTV files to prevent blocking the main thread.
* Never hardcode localized time logic; always use UTC for EPG parsing before converting to the local device timezone.
* When updating UI layouts, ensure all focus states and borders behave correctly for D-pad directional navigation.
* 
