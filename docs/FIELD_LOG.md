# Field log

One entry per real-device test. Newest first. Numbers come from the app's status row or from `adb logcat -s Firefly/Battery`.

| Date | Step (TESTING.md) | Phones | Build | Result | Notes |
|---|---|---|---|---|---|
| 2026-09-23 00:34 | 1 (two phones) | 2 (models TBC) | `373ac36` debug | **Pass (mechanism).** Both dots on test map at 9 m, other phone "6s ago", 0 hops, −83 dBm. tx 19 · rx 7 · dup 60. GPS 3 m. | Indoors, late night. Off-venue test map used (venue 840 km away). 20 m spacing and the ≤ 3 %/h drain reading still to be recorded. dup/rx ≈ 8.5× is the radio repeating one beacon for its whole interval, not relay storms — Phase 5 stats should count those separately. |
| 2026-09-23 00:23 | 1 (two phones) | 2 (models TBC) | `bf2ae32` debug | Pass (radio). 1 heard at 3 m, −51 dBm, 0 hops. tx 4 · rx 4 · dup 12. GPS 4 m. | Dots off-screen because the placeholder venue is in Mumbai; led to the off-venue test map in `373ac36`. |
