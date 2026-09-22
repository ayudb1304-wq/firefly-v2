# Roadmap — phased build plan

Work strictly in order. Each phase ends when its exit criteria pass on real devices.

## Phase 0 — Scaffold (1–2 days)
- Android project, Kotlin DSL, version catalog, Compose, Room, DataStore.
- Package structure from CLAUDE.md. Empty `AppContainer`.
- `core/` as a pure-Kotlin Gradle module (`:core`) with JUnit5.
- CI-less: just `./gradlew test` and `installDebug` must work.
**Exit:** app launches to a placeholder Home screen; `:core` has one passing test.

## Phase 1 — Two phones, one dot (3–5 days)
- `PacketCodec` encode/decode + round-trip tests for every type.
- `GroupCode` gen/validate/CRC-32.
- `FireflyService` with `Advertiser` (own BEACON) and `Scanner` (parse, log to Logcat).
- `LocationSource` with Fused provider, movement classifier.
- Permissions onboarding.
- Home: create/join group (typed code only). Map screen: free grid map (or venue image if a pack is loaded) + my dot + one dot per heard member.
**Exit:** Phone A and B in the same group see each other's dots update within 30 s at 20 m apart in the open. Battery drain logged.

## Phase 2 — Codebook & timeline (3–4 days)
- `Codebook` model, 12 codes.
- Codebook sheet UI, recipient chip, 3× send.
- ACK type, "sent → seen" status.
- Timeline screen, banner + vibration on receive.
- NAME packets; member list with names, distance, bearing.
- QR create/scan for join.
**Exit:** two phones exchange pings with ACKs; codebook usable in ≤ 2 taps.

## Phase 3 — Relay & geo-routing (5–7 days)
- `DedupeCache`, `RelayPolicy` with full case table tests.
- `RelayQueue` with jitter, burst, rate limit, priority.
- Hops/TTL handling; map shows hop count.
- SOS long-press with priority flood.
**Exit:** three-phone test — A and C out of direct range, B between them; A's ping reaches C via B with hops = 1. Dedupe counters confirm no storms.

## Phase 4 — Rendezvous & lighthouse (3–4 days)
- `GeoMath` midpoint, `Rendezvous.suggest`, staleness warning.
- "Meet" flow: propose → accept/counter.
- Lighthouse screen + pattern from senderId; `LIGHTHOUSE_ON` ping shows the pattern to others.
**Exit:** five-phone walk test in a park: any two members can agree a POI in ≤ 3 taps and find each other.

## Phase 5 — Field-test tooling (2–3 days)
- `packet_log` ring buffer, CSV export, Stats screen.
- Settings for scan duty and advertise intervals.
- OEM battery-optimisation help card (Xiaomi, Vivo, Samsung).
- Crash-safe: service restart on crash, `RadioState.Degraded` handling.
**Exit:** dense-crowd test (cricket match / procession) produces a CSV; drain ≤ 3%/h on at least two devices.

## Phase 6 — Totem firmware (optional, after organiser approval) (3–5 days)
- ESP32 (NimBLE) relay: scan, dedupe, relay with TTL 15, TOTEM beacon.
- Config via serial: lat/lon, name.
**Exit:** two phones out of range of each other exchange pings via one totem.

## Phase 7 — Launch hardening (5–7 days)
- Play Store listing, privacy policy, permission justification video.
- Venue pack for the pilot event (surveyed corners, POIs), distributed as a zip — the app itself stays venue-agnostic.
- Onboarding copy, accessibility pass, dark mode.
- Encryption of content fields (v1.1) if time permits.
**Exit:** submitted to Play Store ≥ 4 weeks before the event.

## Deferred
- iOS app (Swift, CoreBluetooth).
- Camera-based lighthouse detection.
- Anonymised density heatmap.
- Multi-group; venue pack delivery by QR.
- BLE 5 extended advertising path.
