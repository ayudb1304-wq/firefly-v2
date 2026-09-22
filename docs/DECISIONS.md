# Decision log

| Date | Decision | Context | Alternatives considered |
|---|---|---|---|
| 2026-09-22 | Android-only v1 | iOS background BLE advertising is restricted; 90%+ of target users on Android | Cross-platform (Flutter/RN) — rejected, BLE plugins immature for advertising |
| 2026-09-22 | Advertising-only mesh, no GATT | Connections fail at radio density | Bridgefy-style connections; Wi-Fi Aware (Android-only, patchy OEM support) |
| 2026-09-22 | 24-byte fixed packet, codebook not chat | Must fit legacy advert; survives congestion | Extended advertising (BLE 5 gating); fragmentation (fragile) |
| 2026-09-22 | Geo-routed relay with flood fallback | Reduce rebroadcast volume | Pure flooding (Bridgefy) — storms at scale |
| 2026-09-22 | No encryption in v0 | Speed to first field data | Encrypt from day one — adds a week; deferred to v1.1 with content-only encryption |
| 2026-09-22 | Static map image + JSON calibration | Offline, simple | Tile-based offline maps — heavier, unnecessary for one venue |
| 2026-09-22 | No DI framework | Small app, one container | Hilt — fine later if the app grows |
