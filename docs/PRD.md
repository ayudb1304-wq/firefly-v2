# Product Requirements Document — Firefly v1 (Android)

## 1. Users
- **Primary:** festival attendees aged 18–35, groups of 2–8 friends, mid-range Android phones, phones at 40–60% battery by evening.
- **Secondary:** event crew and volunteers (pilot seeding cohort).

## 2. Core jobs to be done
1. "Where is everyone?" — see my group on the venue map.
2. "Tell them something quick" — send a pre-coded message without typing.
3. "Let's meet" — agree a place without a conversation.
4. "I'm close but can't see you" — find them in the crowd.
5. "I'm in trouble" — alert my group.

## 3. User stories & acceptance criteria

### Epic A — Groups
**A1. Create group.** As a user I can create a group and get a 6-character code and QR.
- AC: code is uppercase, unambiguous alphabet (no 0/O/1/I), generated locally.
- AC: group ID = CRC-32 of the code (see PROTOCOL).

**A2. Join group.** As a user I can join by typing the code or scanning the QR.
- AC: joining takes < 60 s from app open.
- AC: after joining, my display name (≤ 8 ASCII chars) is broadcast via a NAME packet every 30 s for the first 5 min, then every 5 min.

**A3. Leave group.** I can leave; local data for that group is wiped.

**A4. One group at a time** in v1.

### Epic B — Positions
**B1. Beacon.** While the app is running (foreground or foreground service) my phone broadcasts a BEACON packet with my position.
- AC: interval 3 s when moving (> 1 m/s), 10 s when stationary, 30 s when GPS accuracy > 50 m.
- AC: beacon carries GPS accuracy bucket.

**B2. Group map.** I see a static venue map with a dot per group member, my own dot, and a fading ring for members not heard from in > 60 s.
- AC: dot shows name, "last seen Xs ago", hop count, and accuracy radius.
- AC: members not heard in > 10 min shown greyed in the list with last known position.

**B3. Member list.** A list view with distance and bearing to each member ("Priya · 140 m · ↗").

### Epic C — Codebook messages
**C1. Send ping.** I can tap a message from a grid and send it to one member or the whole group.
- AC: sending requires ≤ 2 taps from the map screen.
- AC: message shows "sent", then "seen" when an ACK arrives.

**C2. Receive ping.** Incoming pings show as a banner + vibration + optional sound, and in a timeline.

**C3. Codebook (v1):**
| Code | Meaning | Arg |
|---|---|---|
| 0x01 | Where are you? | — |
| 0x02 | Meet at [POI] | POI index |
| 0x03 | On my way | — |
| 0x04 | Stay where you are | — |
| 0x05 | Going to [POI] | POI index |
| 0x06 | Back in [N] minutes | minutes (1–60) |
| 0x07 | HELP / SOS | — |
| 0x08 | Lighthouse ON | — |
| 0x09 | OK / ACK | seq being acknowledged (low byte) |
| 0x0A | Low battery, going quiet | — |
| 0x0B | Call me if you can | — |
| 0x0C | Leaving venue | — |

### Epic D — Rendezvous
**D1. Suggest meeting point.** When I tap "Meet [member]", the app proposes the POI nearest the geographic midpoint between us and sends `MEET_AT` to them.
- AC: if either position is > 10 min old, warn before suggesting.
- AC: the receiver can accept (sends ACK + ON_MY_WAY) or counter-propose another POI.

### Epic E — Lighthouse
**E1. Flash mode.** I can turn my screen into a full-brightness flashing pattern unique to me (derived from senderId: 3-colour sequence).
- AC: broadcasts `LIGHTHOUSE_ON`; group members see "Priya is flashing: red-white-red" with the pattern shown on their screen.
- AC: auto-off after 2 minutes.
- (v2) camera detection of the pattern.

### Epic F — Safety
**F1. SOS.** A long-press SOS sends `HELP` with priority flag; relays ignore geo-routing and flood with max TTL.
- AC: receivers get a persistent, loud alert with the sender's position.

### Epic G — Field-test tooling
**G1. Packet log.** Every received packet is logged with timestamp, RSSI, hops, senderId, seq, type.
**G2. Export.** Share the log as CSV via the Android share sheet.
**G3. Stats screen.** Live counters: packets sent/received/relayed/deduped, unique senders heard, battery % and drain rate.

### Epic H — Venue
**H1. No venue required.** With no venue pack the map is a north-up metric grid centred on me that auto-zooms to keep the group in view, with a scale bar. Firefly must be fully usable at any crowd this way.

**H2. Venue pack (optional).** A zip with `map.png` and `venue.json` (name, corner coordinates, POIs), made by an organiser and loaded by the user from the map screen. See `docs/VENUE_PACK.md`. Nothing venue-specific is bundled in the APK.

## 4. Non-functional requirements
| Area | Requirement |
|---|---|
| Battery | ≤ 3%/hour with service running; measured on a mid-range 2022 device |
| Startup | Cold start to map < 3 s |
| Offline | No INTERNET permission in the manifest |
| Permissions | BLUETOOTH_ADVERTISE, BLUETOOTH_SCAN, BLUETOOTH_CONNECT (needed for adapter state on 12+), ACCESS_FINE_LOCATION, FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION/CONNECTED_DEVICE, POST_NOTIFICATIONS, CAMERA (QR) |
| Devices | Android 8.0+; must be tested on at least one each of Xiaomi/Redmi, Samsung A-series, Vivo/Realme, OnePlus/Pixel |
| Accessibility | Codebook buttons ≥ 48 dp, high-contrast map dots, haptics on receive |
| Privacy | No accounts. No server. Session data cleared on "Leave group". Optional "keep log for export" toggle |

## 5. Screens (v1)
1. **Onboarding** — permissions walkthrough with plain-language reasons.
2. **Home** — Create group / Join group.
3. **Map** — venue image, dots, bottom sheet with member list, FAB for codebook.
4. **Codebook sheet** — 12 buttons, recipient chip (All / member).
5. **Timeline** — sent/received pings.
6. **Lighthouse** — full-screen flash.
7. **Stats / Export** — field-test tooling.
8. **Settings** — display name, venue pack, keep-log toggle, battery optimisation help.

## 6. Open questions
- Should beacons be encrypted in v1 (group key from join code) or v1.1? Default: v1.1, with the group ID acting as a filter only. Note this on onboarding.
- POI list per venue: who maintains it? For Lolla, we draft it from the published site map and confirm with the organiser.
- Do we show non-group Firefly users as anonymous density dots? Default: no in v1; heatmap is v2.
