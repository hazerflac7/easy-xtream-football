# Agent Instructions for Sports IPTV Project

## Project Overview
This project is a sports IPTV manager. It handles M3U playlists, XMLTV EPG (Electronic Program Guide) data, and stream categorization for live sports.

## Tech Stack
* **Language:** [e.g., TypeScript / Node.js]
* **Framework:** [e.g., Express / React]
* **Testing:** [e.g., Jest]

## Coding Guidelines
* Always validate M3U URLs before adding them to the database.
* Ensure EPG parsers handle missing timezones gracefully.
* Follow strict linting rules; run `npm run lint` before finalizing code.
* Do not expose stream credentials or authentication tokens in client-side code.

## Environment Setup
To run tests locally, install dependencies using `npm ci` and copy `.env.example` to `.env`.
