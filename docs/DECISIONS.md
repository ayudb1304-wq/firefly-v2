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
| 2026-09-22 | Kotlin package `com.firefly.app`; Play application ID stays `in.firefly.app` | `in` is a hard Kotlin keyword, so `package in.firefly.app` needs backticks in every file and import. The applicationId is what users and the Play Store see and is unaffected. | Backticked `in.firefly.app` packages everywhere — rejected, constant friction and IDE noise |
| 2026-09-22 | Toolchain: AGP 9.4.1 with built-in Kotlin (no `kotlin-android` plugin), Kotlin 2.4.20, KSP 2.3.12, Gradle 9.7.1, JDK 21, compileSdk 37.2 / targetSdk 37 / minSdk 26 | Latest stable everything per CLAUDE.md. Android 16+ ships minor SDK versions, so `compileSdkMinor = 2` is set alongside `compileSdk = 37`. | Pin to AGP 8.x — rejected, would be a forced migration within months |
