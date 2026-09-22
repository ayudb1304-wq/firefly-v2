# Venue packs (optional)

Firefly works anywhere without any setup: with no venue pack it draws a north-up metric grid centred on you that zooms to keep your group in view. A venue pack adds a real site map and named points of interest (POIs) for a specific event. Organisers make one; attendees load it from the map screen (⋮ → *Load venue pack*). It lives in app-private storage until removed.

## Format
A zip file containing:

| File | Required | Notes |
|---|---|---|
| `venue.json` | yes | Calibration + POIs, see below |
| the image named in `venue.json` (`map.png` by default) | yes | PNG or JPEG, north-up preferred, ≤ 4096 px on the long side |

```json
{
  "name": "Example Venue",
  "image": "map.png",
  "corners": {
    "tl": [19.0000, 72.8200],
    "tr": [19.0000, 72.8300],
    "bl": [18.9900, 72.8200],
    "br": [18.9900, 72.8300]
  },
  "pois": [
    { "i": 1, "name": "Main Stage", "lat": 18.9950, "lon": 72.8250 }
  ]
}
```

- `corners` are the WGS84 `[lat, lon]` of the image's four corners. Survey them with a phone standing at each corner or read them off a satellite map. A small rotation is absorbed by the affine fit; large rotations should be corrected in the image.
- `pois` are numbered 1–255. Codebook messages refer to POIs by `i` (Phase 2), so every phone in a group should have the same pack loaded.
- Everything is unencrypted and public by design.

## Building one
```bash
cd venue
cp map.example.png map.png            # replace with the real map
zip ../my-venue.zip venue.json map.png
```
Send the zip to attendees any way you like (chat, QR to a download, USB). Nothing about it needs a server.

## Behaviour on the phone
- If a pack is loaded and you are inside its corners, the image is shown.
- If you are outside it (travelling to the event, testing at home), the free grid map is shown and the caption says how far the venue is.
- *Remove venue* deletes the pack from the phone.
