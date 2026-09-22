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
- Export CSV from every phone at the end; name `test<step>_<phone>_<date>.csv`.
- Scripted scenarios (step 3+): (a) "Where are you" ping at 30 m, 60 m, 120 m; (b) MEET_AT and time to physical reunion; (c) SOS from a corner of the crowd.

## Metrics computed from CSVs
- Delivery rate = pings ACKed / pings sent.
- Latency = ACK ts − send ts.
- Hop distribution.
- Duplicate ratio = deduped / received (should stay < 3× at density).
- Packets/sec per node (congestion indicator).
- Drain %/hour.

## Codebook coverage (step 4)
Hand out a paper slip: "what did you actually want to say?" Count messages not expressible in the 12 codes. Target ≤ 10%.

## Known device gotchas
- Xiaomi/Redmi: MIUI kills foreground services unless "No restrictions" + autostart enabled.
- Vivo/Realme: "High background power consumption" prompt must be allowed.
- Samsung: "Sleeping apps" list.
- Android 12+: BLUETOOTH_SCAN needs `neverForLocation` flag if we do not want location derived from BLE; we *do* use location, so omit the flag and request FINE_LOCATION.
