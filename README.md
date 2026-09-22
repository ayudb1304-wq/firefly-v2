# Firefly

Offline friend-finding for large crowds. No signal needed.

Phones broadcast a 24-byte BLE advertisement with their GPS position and a short coded message. Every Firefly phone nearby relays it. A static venue map shows where your group is; the app suggests a meeting point; a screen-flash "lighthouse" gets you the last 30 metres.

**Status:** Phase 1 built (beacons, scanner, map); awaiting the two-phone field test — see `docs/ROADMAP.md`. Target pilot: Lollapalooza India 2027.

## Quick start (developer)
Requires JDK 17+ and the Android SDK (platform 37.2, build-tools 37.0.0). Android Studio installs both; without it, `brew install openjdk@21 android-commandlinetools` and set `sdk.dir` in `local.properties`.
```bash
git clone https://github.com/ayudb1304-wq/firefly-v2.git
cd firefly-v2
./gradlew :core:test :app:testDebugUnitTest   # unit tests (pure-Kotlin protocol logic lives in :core)
./gradlew :app:installDebug                   # to a connected phone
```
Requires two physical Android phones (API 26+) with Bluetooth and GPS for anything past Phase 0. Emulators do not support BLE advertising.

## Quick start (tester)
1. Install the APK on two phones.
2. Grant Bluetooth, Location and Nearby Devices permissions.
3. On phone A: **Create group** → note the 6-letter code.
4. On phone B: **Join group** → enter the code.
5. Walk apart. Watch the dots on the map.

## Docs
| File | What it is |
|---|---|
| `CLAUDE.md` | Instructions for Claude Code. Start here. |
| `docs/BRD.md` | Business requirements |
| `docs/PRD.md` | Product requirements & user stories |
| `docs/ARCHITECTURE.md` | System design |
| `docs/PROTOCOL.md` | Packet format & relay rules |
| `docs/ROADMAP.md` | Phased build plan |
| `docs/TESTING.md` | Field test plan |
| `docs/DECISIONS.md` | Decision log |
| `docs/KICKOFF_PROMPT.md` | First prompt to paste into Claude Code |

## Contributing
Work in phase order from `docs/ROADMAP.md`. Every change to the packet format needs a `PROTOCOL.md` update and a codec round-trip test.

## Licence
TBD (recommend MIT for the app, keep the protocol spec open).
