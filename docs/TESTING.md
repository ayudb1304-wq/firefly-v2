# Field test plan

## Ladder
| Step | Phones | Where | What we learn | Pass |
|---|---|---|---|---|
| 1 | 2 | Empty park / terrace | Range in open air, GPS drift, battery, UX | Dots update ≤ 30 s at 20 m; ≤ 3%/h |
| 2 | 3 | Mall / metro station / apartment block | Multi-hop through walls and bodies, dedupe | A→C via B, hops = 1, zero storms |
| 3 | 5–10 | Crowded market / cricket match / religious procession / large wedding | First real congestion, OEM battery killers | ≥ 70% pings delivered ≤ 60 s |
| 4 | 50–100 | College fest | Adoption flow, codebook coverage, relay at scale | Same, plus onboarding ≤ 60 s |
| 5 | Crew (~50–200) | Lolla venue build-up | Real venue map, totems if any, GPS near stages | Dry run passes |
| 6 | Pilot cohort (≥ 500) | Lolla 2027 | Case-study numbers | BRD §8 targets |

## Per-test checklist
- Same APK build on all phones; note build hash.
- Battery % at start and end; screen-on time.
- Device list with OEM/OS version.
- Export CSV from every phone at the end: ⋮ → *Stats & export* → *Export CSV* (share to yourself). Files are named `firefly_<group>_<radioId>_<device>_<date>.csv`; put them in a folder named `test<step>_<date>/`. Turn on *Keep log after leaving a group* before the test or leaving the group wipes it.
- Scripted scenarios (step 3+): (a) "Where are you" ping at 30 m, 60 m, 120 m; (b) MEET_AT and time to physical reunion; (c) SOS from a corner of the crowd.

## Reading the CSV
One row per event. `event` is `TX` (we put it on air), `RX` (first copy heard), `DUP` (repeat copy, dropped), `RELAY` (we re-broadcast it), `FOREIGN` (another group), `BATTERY` (level sample each minute) or `EVENT` (service start/stop, settings, degraded state, crash). `sender`/`target` are radio IDs in hex; `code` is the codebook code (0 = beacon); `rssi` is dBm on RX/DUP; `bytes` is the raw 24-byte packet on TX/RX.

## Metrics computed from CSVs
- Delivery rate = distinct (sender, seq) of `TX` rows with type 1 that have a matching `RX` row of type 3 (ACK) with `arg` = seq & 0xFF, divided by distinct `TX` pings.
- Latency = ts of that ACK `RX` − ts of the first `TX` of the ping.
- Hop distribution = histogram of `hops` over `RX` rows.
- Duplicate ratio = `DUP` rows / `RX` rows. Note each phone advertises a beacon continuously for its whole interval, so 3–8× is normal even for two phones; watch for growth with crowd size, not the absolute value.
- Packets/sec per node = (`RX` + `DUP` + `FOREIGN`) rows per second (the *Air load* number on the Stats screen).
- Drain %/hour = from `BATTERY` rows: (first level − last level) / hours, excluding periods where `charging=true`.

## Codebook coverage (step 4)
Hand out a paper slip: "what did you actually want to say?" Count messages not expressible in the 12 codes. Target ≤ 10%.

## Known device gotchas
- Xiaomi/Redmi: MIUI kills foreground services unless "No restrictions" + autostart enabled.
- Vivo/Realme: "High background power consumption" prompt must be allowed.
- Samsung: "Sleeping apps" list.
- Android 12+: BLUETOOTH_SCAN needs `neverForLocation` flag if we do not want location derived from BLE; we *do* use location, so omit the flag and request FINE_LOCATION.
