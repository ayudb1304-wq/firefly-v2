# CLAUDE.md — Firefly

Firefly is an **offline, phone-to-phone "find and meet" app for large crowds** (music festivals, stadiums, religious gatherings). It works with **no cellular data, no Wi-Fi, no server**. Phones broadcast tiny Bluetooth Low Energy (BLE) advertisement packets carrying their GPS position and a short coded message; every other phone running Firefly relays them. It is venue-agnostic: it works anywhere with no setup (free grid map), and an organiser can optionally supply a venue pack for a real site map. First pilot target: Lollapalooza India 2027, but nothing may be hard-coded to one event.

Read these before writing any code, in this order:
1. `docs/BRD.md` — why this exists and what "done" means for the business
2. `docs/PRD.md` — user stories and acceptance criteria
3. `docs/ARCHITECTURE.md` — modules, data flow, key decisions
4. `docs/PROTOCOL.md` — the exact 24-byte packet format and relay rules (this is the contract; do not change it without updating the doc and bumping the version nibble)
5. `docs/ROADMAP.md` — phased build plan. Work in phase order. Do not start a phase until the previous phase's exit criteria pass.
6. `docs/TESTING.md` — how we test with 2, 3, and 50 phones

## Stack (fixed — do not substitute)
- Android only for v1. Kotlin, Jetpack Compose, Material 3.
- minSdk 26 (Android 8.0), targetSdk latest stable, compileSdk latest stable.
- Coroutines + Flow for async. Room for local storage. DataStore for prefs.
- Android BLE APIs directly (`BluetoothLeAdvertiser`, `BluetoothLeScanner`). **No third-party BLE or mesh libraries.**
- `FusedLocationProviderClient` for GPS.
- Maps: free north-up metric grid by default; optional static venue pack (PNG + JSON calibration, loaded from a zip, never bundled). **No Google Maps / Mapbox** — they need internet.
- Manual dependency injection via a single `AppContainer`. No Hilt/Dagger/Koin in v1.
- Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`).
- Tests: JUnit5 + kotlinx-coroutines-test for unit tests; Compose UI tests only for critical screens.

## Package layout
Kotlin package is `com.firefly.app` (the Play application ID is `in.firefly.app`; `in` is a Kotlin keyword — see `docs/DECISIONS.md`). `core/` lives in its own Gradle module `:core`.
```
com.firefly.app
├── core/        # pure Kotlin, no Android deps: packet codec, routing logic, geo maths, codebook
├── radio/       # BLE advertise/scan, foreground service, duty cycling
├── location/    # GPS wrapper
├── data/        # Room entities, DAOs, repositories
├── venue/       # optional venue pack import/storage
├── ui/          # Compose screens + view models
└── di/          # AppContainer
```
`core/` must have **zero** Android imports so it can be unit-tested on the JVM and later ported to iOS/ESP32.

## Non-negotiable rules
- **Packet layout is sacred.** Anything that touches bytes goes through `core/protocol/PacketCodec.kt` and has a round-trip unit test.
- **Never open a BLE connection** (no GATT). Firefly is advertising + scanning only.
- **Dedupe before relay.** Every received packet is checked against the `(senderId, seq)` LRU cache before any processing.
- **Jitter before rebroadcast.** Random 50–300 ms delay. Never rebroadcast immediately.
- **No network calls.** The app must have no `INTERNET` permission in v1. If you find yourself adding one, stop.
- **Battery is a feature.** Scanning is duty-cycled; advertising interval adapts to movement. Log battery drain per hour in field-test builds.
- **Privacy defaults.** Positions are only shared with the joined group. Nothing is persisted beyond the current session unless the user opts in.
- **Field-test logging is first-class.** Every received packet is logged (timestamp, RSSI, hops, senderId, seq) to a CSV the user can export. This is how we learn what the crowd does to us.

## How to work
- Before implementing a phase, restate its exit criteria from `docs/ROADMAP.md` and list the files you will touch.
- Prefer small commits per feature. Commit message format: `phaseN: <what>`.
- When you are unsure about a product decision, check `docs/PRD.md`; if it's not there, make the simplest choice, note it in `docs/DECISIONS.md`, and continue.
- Run `./gradlew :app:testDebugUnitTest` before declaring any phase done.
- Do not build iOS, the ESP32 totem firmware, or any server until explicitly asked. They are documented for context only.

## Vocabulary
- **Beacon** — a position-only packet, sent periodically.
- **Ping** — a coded message packet (from the codebook).
- **Group** — friends sharing a 6-character join code. All packets are tagged with the group's ID.
- **POI** — a named point on a venue pack's map (Stage A, Bar 2, Toilets North). Codebook messages reference POIs by index; without a venue pack, "meet here" style messages use the sender's position instead.
- **Rendezvous** — the app-suggested POI nearest the midpoint between two friends.
- **Lighthouse** — screen-flash mode for the last 30 metres.
- **Totem** — an optional fixed ESP32 relay node placed by the organiser (Phase 6, not v1).
