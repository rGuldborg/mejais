# Mejais Production-Readiness Summary

## Product Overview
- **Name:** Mejais – League of Legends draft assistant (Windows desktop, JavaFX).
- **Purpose:** Mirrors the live champion select state from the local League Client and recommends champions based on aggregated high-ELO statistics stored in `data/snapshot.db`.
- **Scope:** Read-only companion. Mejais never injects input, blocks picks/bans, or manipulates the League client; it only displays statistical insights while the player drafts.
- **Audience:** Solo/duo players who want a lightweight companion during champ select.

## Architecture & Data Flow
1. **Snapshot Builder (maintainer-only tooling):**
   - Uses Riot’s public APIs (`match-v5`, `league-v4`, `summoner-v4`, etc.) via `collector/` to periodically download high-ELO ranked match data.
   - Aggregates the results into champion-level win/play counts, per-role frequency, and synergy/counter aggregates.
   - Stores only anonymized statistics inside the SQLite snapshot (`data/snapshot.db`). No match IDs, Summoner Names, PUU IDs, Riot IDs, or timestamps are persisted. Raw match payloads are discarded immediately after the aggregates are computed.
   - Requires a developer/production API key set through environment variables. The desktop app itself never prompts end users for any Riot credentials or keys.
2. **Desktop Client (distributed app):**
   - Loads the prebuilt `snapshot.db` file and queries the aggregated tables via `StatsDao` (read-only).
   - Subscribes to the local League Client API WebSocket (`/lol-champ-select/v1/session`) over HTTPS using the lockfile credentials to mirror picks/bans.
   - Renders recommended champions and the OP/SY/CO bars purely for display.
3. **Static Assets:** Champion names, IDs, roles, and icons are bundled from Data Dragon at build time; no runtime scraping occurs.

## Data Handling & Privacy
- **Stored Data:** Only aggregated champion metrics (wins, plays, role counts, synergy/counter win ratios). No personally identifiable information, match identifiers, or timeline data is retained or redistributed.
- **Retention:** New snapshots overwrite the previous file; the tooling keeps raw Riot responses in memory only long enough to compute the aggregates, keeping total retention well under 30 days.
- **Distribution:** The packaged client includes a static snapshot. Optional remote snapshot downloads are hosted on an HTTPS endpoint controlled by the maintainer, and the payload is the same aggregated SQLite file.
- **Logging:** Application logs contain only high-level status (e.g., “Loaded snapshot version X”, “LCU unavailable”). No champ-select payloads, Summoner info, or identifiers are logged.

## Security Controls
- **API key management:** Keys live only in maintainer environments (environment variables + `config.example`). Nothing in the repository or shipped binaries contains a Riot API key.
- **Transport security:** All Riot REST calls use Java’s `HttpClient` over HTTPS. LCU connections trust the official `riotgames.pem` certificate shipped with the League client; if the certificate is missing, the app warns the user rather than disabling TLS checks.
- **Rate limiting:** `RiotRateLimiter` enforces both the 20 req/s and 100 req/2 min policies by default, with configurable buckets per region. Collector jobs run sequentially and respect retry-after headers.
- **Local storage:** Snapshot SQLite file lives under `data/`. No other sensitive files (locks, credentials) are persisted.
- **Signing & delivery:** The packaging script now supports Authenticode signing so shipped binaries identify the publisher and pass SmartScreen review.

## Game Integrity
- Mejais is a read-only overlay/companion. It provides no automation, scripting, dodging, or queue manipulation features.
- Only shows information already visible (team comps) plus aggregated public stats. There is no prediction of unseen enemy picks or dodging guidance.
- No attempts to track, de-anonymize, or persist player identities.
- UI clearly states that Mejais is unaffiliated with Riot Games (README + Help view).
- The app does not modify League files, DLLs, or memory.

## Requested Production Access
- **APIs:** `summoner-v4`, `league-v4`, `match-v5`, `champion-mastery-v4` (for filtering by rank) are sufficient. No spectator endpoints are consumed.
- **Rate needs:** Standard production limits (500 requests / 10s, 30k / 10m) cover the nightly snapshot rebuilds. Internal throttling already caps bursts well below those thresholds.
- **Regions:** Global (americas, europe, asia routing values) to gather high-ELO ranked matches across NA, EUW, EUNE, KR, etc.

## Contact & Operations
- Maintainer: [Your Name] (rguldborg@proton.me / Discord: rguldborg). Included in developer portal entry.
- Monitoring: Packaging script logs + application logs are reviewed each release. Snapshot jobs run via scheduled CI and alert on failures.
- User support: README + Help tab document usage, privacy, and Riot disclaimer.

## Compliance Statement
Given the above controls, Mejais aligns with Riot’s Developer Policies, including General Policies (no redistribution of personal data, appropriate attribution), Game Integrity (no automation or cheating assistance), and Data Handling (secure storage and minimum retention). The production key will only power the offline snapshot builder, and the released desktop client consumes pre-aggregated data without exposing Riot credentials or raw match data. We request production approval to continue providing the draft assistant within these compliance boundaries.
