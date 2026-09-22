# Firefly Protocol v0 — packet format & relay rules

This is the contract between all Firefly nodes (Android, later iOS and ESP32 totems). Change it only by bumping the version nibble and updating this file.

## 1. Transport
- BLE **legacy advertising**, non-connectable, non-scannable (`ADV_NONCONN_IND`).
- Payload carried in a single **Manufacturer Specific Data** AD structure.
- Company ID: `0xFFFF` (test/development ID) during development. Register a real one before public launch.
- AD overhead: 1 (length) + 1 (type 0xFF) + 2 (company ID) = 4 bytes → **27 bytes available**. We use **24**.
- No flags AD structure (Android adds none for non-connectable when `setIncludeDeviceName(false)` and TX power excluded). If the OEM forces flags (3 bytes), 24 still fits.
- Android: prefer `AdvertisingSet` with legacy mode on API 26+; fall back to `BluetoothLeAdvertiser.startAdvertising`. Extended advertising (BLE 5) is a v1.1 optimisation, not required.

## 2. Packet layout (24 bytes, big-endian)
| Offset | Size | Field | Notes |
|---|---|---|---|
| 0 | 1 | `ver_type` | high nibble = protocol version (0), low nibble = packet type |
| 1 | 4 | `groupId` | CRC-32 of the 6-char join code (uppercase ASCII) |
| 5 | 2 | `senderId` | random u16 per install, regenerated on "Leave group"; 0x0000 reserved |
| 7 | 2 | `seq` | per-sender monotonic counter, wraps |
| 9 | 1 | `ttl_hops` | high nibble = TTL remaining (0–15), low nibble = hops so far |
| 10 | 4 | `lat` | i32, degrees × 1e6 (WGS84) |
| 14 | 4 | `lon` | i32, degrees × 1e6 |
| 18 | 1 | `flags` | bit7 = priority (SOS), bit6 = ACK requested, bits 3–0 = accuracy bucket |
| 19 | 1 | `code` | codebook code (0x00 = BEACON, no message) |
| 20 | 1 | `arg` | code argument (POI index, minutes, ack seq low byte) |
| 21 | 2 | `target` | senderId of recipient; `0xFFFF` = whole group |
| 23 | 1 | `ts` | sender's seconds-since-epoch mod 256 (freshness hint only) |

**Accuracy bucket** (flags bits 3–0): 0 = unknown, 1 = < 10 m, 2 = < 25 m, 3 = < 50 m, 4 = < 100 m, 5 = ≥ 100 m.

## 3. Packet types (low nibble of byte 0)
| Type | Name | Notes |
|---|---|---|
| 0x0 | BEACON | position only; `code` must be 0x00 |
| 0x1 | PING | codebook message; carries sender position too |
| 0x2 | NAME | bytes 10–17 reinterpreted as 8 ASCII chars display name; lat/lon absent |
| 0x3 | ACK | `arg` = low byte of acknowledged seq, `target` = original sender |
| 0x4–0xE | reserved | |
| 0xF | TOTEM | fixed relay node beacon (Phase 6) |

## 4. Sending rules
- BEACON interval: 3 s moving, 10 s stationary, 30 s if accuracy bucket ≥ 4. Add ±20% random jitter.
- PING: send 3 times, 1 s apart, same `seq`. Receivers dedupe.
- SOS (priority flag): TTL = 15, send 5 times over 10 s, repeat every 60 s until cancelled.
- Default TTL: 6. NAME: 4. ACK: 6.
- `seq` increments per packet *origination*, not per retransmission.

## 5. Receive & relay rules
Every node runs this on each received advert:
1. Parse. Drop if version ≠ 0, or `groupId` ≠ my group (v1: single group).
2. **Dedupe:** key = `(senderId, seq)`. If in LRU cache (size 512, 5-min expiry) → drop. Else insert.
3. Update local state (member position, name, timeline, ACKs).
4. If `senderId` == me → stop.
5. If TTL == 0 → stop.
6. **Relay decision:**
   - Priority flag set → relay.
   - `target == 0xFFFF` (group broadcast) → relay.
   - Targeted packet:
     - If I *am* the target → send ACK if requested; do not relay.
     - Else if I know the target's last position (< 10 min old): relay only if `dist(me, target) < dist(sender_pos, target) − 15 m` (I am closer than where it came from). This is the **geo-routing gate**.
     - Else (target unknown) → relay (fallback flood).
7. Decrement TTL, increment hops, **wait random 50–300 ms**, then advertise the packet for **500 ms** (one burst), then return to own beacon.
8. Rate limit relays: max 10 relayed packets per 5 s per node; drop lowest-priority extras.

## 6. Scanning
- Foreground service. Scan mode `SCAN_MODE_LOW_LATENCY` in 4 s windows, 2 s off (duty cycle 66%). Field-test builds expose this as a setting.
- Filter by manufacturer ID `0xFFFF` at the OS level to reduce wake-ups.
- Record RSSI for every packet for field logs and (v2) proximity estimation.

## 7. Encryption (v1.1, not v0)
- Key = HKDF(join code). Nonce = `groupId ‖ senderId ‖ seq`.
- Encrypt only the **content fields** — lat, lon, flags, code, arg (bytes 10–20) — with ChaCha20. Adds no bytes.
- Routing fields (version, groupId, senderId, seq, TTL, target, ts) stay in the clear so relays can dedupe and geo-route without the key. Trade-off: relays learn *who* is talking to *whom* but not *where* or *what*. Record the decision in DECISIONS.md when implemented.

## 8. Worked example
Priya (senderId 0x1A2B, at 19.0760, 72.8777) sends "Meet at POI 4" to Rahul (0x3C4D):
```
00            ver 0, type PING
9F 8C 21 07   groupId
1A 2B         senderId
00 2A         seq 42
60            TTL 6, hops 0
01 22 F0 60   lat 19076000
04 57 26 C8   lon 72877700
42            flags: ACK requested, accuracy bucket 2
02            code MEET_AT
04            arg POI 4
3C 4D         target Rahul
7B            ts
```
Rahul's phone receives it (possibly via 3 hops), shows the banner, replies with ACK (type 0x3, arg 0x2A, target 0x1A2B).
