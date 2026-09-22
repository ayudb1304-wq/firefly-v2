# Field log

One entry per real-device test. Newest first. Numbers come from the app's status row or from `adb logcat -s Firefly/Battery`.

| Date | Step (TESTING.md) | Phones | Build | Result | Notes |
|---|---|---|---|---|---|
| 2026-09-23 ~01:50 | 2 (pings + ACKs) — **Phase 2 exit** | 2 | `de4b9f8` debug | **Pass.** All replies delivered both ways; vibration, banner, "seen by 1", QR join, timeline all working. Codebook reachable in 2 taps. | Scan 8/2 + 1.5 s repeat spacing fixed the lost replies. |
| 2026-09-23 ~01:35 | 2 (pings + ACKs) | 2 | `0298368` debug | **Mostly pass.** Vibration, banner, "seen by 1" all work. Some replies (e.g. On my way after Meet at my position) never arrived. | Diagnosis: 3×1 s ping span (2.6 s) can sit entirely inside the 4 s scan-off gap. Fixed in next build: scan 8/2, repeats 1.5 s apart. |
| 2026-09-23 ~01:20 | 2 (pings + ACKs) | 2 | `f8146c3` debug | **Fail → fixed in next build.** QR join and message list worked. On receiving a ping the receiving app crashed (looked like it minimised), no vibration, sender never saw "seen". | Root cause: missing `VIBRATE` permission → SecurityException in the service before the ACK was queued. Fixed: permission added, ACK now queued before alerting, alerting wrapped so it can never kill the radio, per-packet try/catch in the pipeline. |
| 2026-09-23 00:34 | 1 (two phones) | 2 (models TBC) | `373ac36` debug | **Pass (mechanism).** Both dots on test map at 9 m, other phone "6s ago", 0 hops, −83 dBm. tx 19 · rx 7 · dup 60. GPS 3 m. | Indoors, late night. Off-venue test map used (venue 840 km away). 20 m spacing and the ≤ 3 %/h drain reading still to be recorded. dup/rx ≈ 8.5× is the radio repeating one beacon for its whole interval, not relay storms — Phase 5 stats should count those separately. |
| 2026-09-23 00:23 | 1 (two phones) | 2 (models TBC) | `bf2ae32` debug | Pass (radio). 1 heard at 3 m, −51 dBm, 0 hops. tx 4 · rx 4 · dup 12. GPS 4 m. | Dots off-screen because the placeholder venue is in Mumbai; led to the off-venue test map in `373ac36`. |
