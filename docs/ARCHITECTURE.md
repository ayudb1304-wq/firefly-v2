# Architecture — Firefly v1 (Android)

## 1. Context & goals
A single Android app, no server. Goals in priority order: (1) works with zero connectivity, (2) survives high radio density, (3) cheap on battery, (4) portable protocol.

## 2. High-level design
```
┌────────────────────────────────────────────────────────────┐
│ ui/  (Compose)                                             │
│  Onboarding · Home · Map · Codebook · Timeline · Lighthouse │
│  Stats · Settings          ── ViewModels observe Flows ──  │
└──────────────▲──────────────────────────────▲──────────────┘
               │                              │
┌──────────────┴──────────┐      ┌────────────┴─────────────┐
│ data/                   │      │ venue/                   │
│ Room: members, pings,   │      │ VenueRepository (zip     │
│ packet_log · repos      │      │ import, optional pack)   │
└──────────────▲──────────┘      └──────────────────────────┘
               │
┌──────────────┴─────────────────────────────────────────────┐
│ core/  (pure Kotlin, JVM-testable)                          │
│  PacketCodec · RelayPolicy · DedupeCache · Codebook         │
│  GeoMath (haversine, midpoint, bearing) · Rendezvous        │
│  GroupCode (gen/validate/CRC-32) · LighthousePattern        │
└──────────────▲──────────────────────────────▲──────────────┘
               │                              │
┌──────────────┴──────────┐      ┌────────────┴─────────────┐
│ radio/                  │      │ location/                │
│ FireflyService (FGS)    │      │ LocationSource (Fused)   │
│ Advertiser · Scanner    │      │ movement classifier      │
│ DutyCycler · RelayQueue │      └──────────────────────────┘
└─────────────────────────┘
```

## 3. Data flow
**Outbound beacon:** `LocationSource` emits fix → `BeaconScheduler` picks interval (moving/stationary/poor accuracy) → builds `Packet` via `PacketCodec.encode` → `Advertiser.setPayload()`.

**Inbound:** `Scanner` callback (bytes, RSSI, timestamp) → `PacketCodec.decode` → `DedupeCache.check` → `PacketLogDao.insert` (field log) → `MemberRepository.update` / `PingRepository.insert` → `RelayPolicy.decide(packet, myPos, knownMembers)` → if relay: `RelayQueue.enqueue(packet, jitter)` → `Advertiser` bursts it for 500 ms then restores own beacon.

**Codebook send:** UI → `PingRepository.send(code, arg, target)` → 3 × advertise 1 s apart → timeline row "sent"; ACK arrival flips to "seen".

**Rendezvous:** UI → `Rendezvous.suggest(myPos, theirPos, venue.pois)` → nearest POI to midpoint → `MEET_AT` ping.

## 4. Key components

### radio/FireflyService
- Foreground service, `foregroundServiceType="connectedDevice|location"`.
- Owns one `Advertiser` and one `Scanner`. Restarts them on Bluetooth toggle.
- Single coroutine scope; all radio work on `Dispatchers.Default`.
- Holds `RelayQueue` (bounded, 32 entries, priority by SOS > targeted > broadcast).

### radio/Advertiser
- Single advertising set. Own beacon is the default payload. Relays temporarily replace the payload (`AdvertisingSet.setAdvertisingData`) for 500 ms, then restore.
- Interval: `ADVERTISE_MODE_LOW_LATENCY` (~100 ms) during bursts; `BALANCED` (~250 ms) for own beacon. Field-test setting exposes these.

### radio/Scanner
- `ScanFilter` on manufacturer ID 0xFFFF. `ScanSettings` low latency, `MATCH_MODE_AGGRESSIVE`, `CALLBACK_TYPE_ALL_MATCHES`.
- `DutyCycler`: 4 s on / 2 s off. Android 7+ enforces a 5-start-per-30-s limit per app — the cycler must respect that (use ≥ 6 s period).

### core/RelayPolicy
Pure function: `(packet, myPos, targetLastPos?, senderPos, now) → Relay | Drop | AckAndStop`. Encodes section 5 of PROTOCOL.md exactly. Fully unit-tested with a table of cases.

### core/DedupeCache
LRU keyed by `(senderId shl 16) or seq`, 512 entries, 5-minute TTL. Pure Kotlin.

### core/geo/MapProjection and FreeMap
With no venue pack, `FreeMap` (pure) decides the free map's width (zoom to fit the group, with hysteresis), when to recentre on me, and the grid spacing; the projection is `MapProjection.centredOn(me, width)`. With a pack, `venue.json` gives the lat/lon of the image's four corners. Compute an affine transform (assume small area, no rotation correction needed beyond the affine fit). `toPixel(lat, lon)` and `toGeo(px, py)`.

```json
{
  "name": "Lollapalooza India 2027 — Mahalaxmi Racecourse",
  "image": "map.png",
  "corners": { "tl": [19.0000,72.8200], "tr": [19.0000,72.8300], "bl": [18.9900,72.8200], "br": [18.9900,72.8300] },
  "pois": [
    { "i": 1, "name": "Main Stage", "lat": 18.9950, "lon": 72.8250 },
    { "i": 2, "name": "Bar 2", "lat": 18.9940, "lon": 72.8270 }
  ]
}
```
(Placeholder coordinates. Replace with surveyed values.)

### data/ (Room)
- `members(senderId PK, name, lat, lon, accuracy, lastSeen, hops, rssi)`
- `pings(id PK, seq, senderId, target, code, arg, direction, status, ts)`
- `packet_log(id PK, ts, event, type, senderId, seq, hops, ttl, target, code, arg, rssi, latE6, lonE6, bytesHex, extra)` — every radio event (`RX`, `DUP`, `FOREIGN`, `TX`, `RELAY`, `BATTERY`, `EVENT`), capped at 50k rows, ring-buffered, written in batches off the radio thread. Exported as CSV via the share sheet.
- `DataStore`: groupCode, senderId, displayName, joinedAt, keepLog, scanOnMs/scanOffMs, advInterval, batteryCardDismissed.

## 5. Key decisions & trade-offs
| Decision | Why | Trade-off |
|---|---|---|
| Advertising only, no GATT | Connections fail at density; broadcast scales | 24-byte payload cap; no free text |
| Legacy adverts, not extended | Works on every API 26+ phone incl. cheap ones | Can't grow payload without BLE 5 gating |
| Geo-routed relay | Cuts rebroadcast volume vs. flooding | Needs recent target position; falls back to flood |
| Free grid map by default, venue image optional | Works at any crowd with zero setup; no internet | Free map has no landmarks; POIs need a pack |
| Foreground service | Only way to scan reliably | Persistent notification; OEM battery-killer risk |
| No encryption in v0 | Ship faster, measure first | Positions readable by anyone with the app and group ID; documented on onboarding; v1.1 fixes |
| Codebook not chat | Fits in a packet; faster in a crowd | Expressiveness limited; validate coverage in tests |

## 6. Threading & lifecycle
- Service is the single owner of radio state. UI binds via a `StateFlow<RadioState>` exposed through the `AppContainer`.
- On app kill: service continues (foreground). On "Leave group": stop service, wipe DB tables except log if `keepLog`.
- Bluetooth off / permission revoked → service enters `Degraded` state, UI shows fix-it card.

## 7. Portability notes (for iOS and totems later)
- Everything in `core/` is protocol logic with no Android types; port line-for-line to Swift and C.
- iOS: CoreBluetooth cannot set manufacturer data in background; foreground-only origination, but iOS *can* scan in background with a service UUID filter — consider adding a 16-bit service UUID AD in v1.1 to enable iOS background scanning/relaying.
- ESP32 totem: NimBLE stack, same 24-byte layout, relay policy in C, no position of its own (fixed lat/lon from config), TTL 15, type 0xF beacon every 5 s.

## 8. Observability (field builds)
- `Stats` screen: sent/received/relayed/deduped counters, unique senders (5 min), packets/sec, battery %/hour.
- CSV export via share sheet. Schema matches `packet_log`.
- Optional debug overlay on the map: RSSI of last packet per member.
