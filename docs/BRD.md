# Business Requirements Document — Firefly

| | |
|---|---|
| **Version** | 0.1 (draft) |
| **Date** | 22 September 2026 |
| **Owner** | Founder / Product |
| **Status** | For review |

## 1. Executive summary
Firefly is a mobile application that lets groups of friends locate and coordinate with each other at large, crowded events **without any mobile network, Wi-Fi or server**. It uses phone-to-phone Bluetooth relaying, GPS, a static venue map and a tiny set of pre-coded messages.

The first deployment target is **Lollapalooza India 2027** (Mumbai, expected Q1 2027 — confirm dates with the organiser). The business goal is to prove the concept in front of an organiser and convert that proof into B2B licensing, sponsorship and crowd-safety contracts with Indian live-event companies.

## 2. Problem statement
At high-density events (60,000+ attendees in a few hectares), mobile networks collapse: cell towers saturate, calls fail, messages queue for minutes or never arrive. Friends who split up — for food, toilets, a different stage — routinely lose each other for hours. Today's workarounds (pre-agreed meeting points, holding phones in the air, shouting) are unreliable and stressful. Organisers absorb the cost as lost-and-found queues, security incidents and negative reviews.

Existing offline-mesh messengers (Bridgefy, Briar) do not solve this in practice because (a) almost nobody has them installed when the network dies, (b) they rely on Bluetooth *connections* which fail at high radio density, and (c) they flood the network with free-text messages that do not survive congestion.

## 3. Vision & objectives
**Vision:** nobody loses their friends at a festival again.

| # | Objective | Measure |
|---|---|---|
| O1 | Prove offline friend-finding works at real crowd density | ≥ 70% of "where are you" pings delivered within 60 s during Lolla 2027 pilot |
| O2 | Demonstrate value to an organiser | Signed pilot agreement with BookMyShow Live (or equivalent) before the event |
| O3 | Generate a sellable case study | Post-event report with delivery rates, reunion times, density heatmaps |
| O4 | Establish a path to revenue | At least one paid conversation with a second event company (Sunburn, NH7, IPL franchise) within 90 days of Lolla |

## 4. Scope

### In scope (v1, Lolla 2027)
- Android app (Kotlin), sideloadable APK and Play Store listing
- Group creation and joining via 6-character code / QR
- Periodic position beacons relayed across phones (multi-hop)
- Codebook messages (~12 pre-defined intents such as "Where are you", "Meet at [POI]", "Help")
- Static venue map with points of interest
- Automatic rendezvous suggestion
- Lighthouse (screen-flash) mode for close-range finding
- Field-test logging and CSV export
- Optional ESP32 relay "totems" (hardware; documented, built only if organiser approves placement)

### Out of scope (v1)
- iOS app (planned v2)
- Free-text chat
- Voice, photos, media
- Any server, cloud sync or accounts
- Payments, ticketing, cashless integration
- Integration into the official festival app (desired outcome, but a business negotiation, not a v1 build dependency)

## 5. Stakeholders
| Stakeholder | Interest | Influence |
|---|---|---|
| Founder / product owner | Ship a working pilot, secure first customer | Owner |
| Festival attendees (users) | Find friends, feel safe, low battery cost | High (adoption) |
| BookMyShow Live / Lollapalooza India | Fewer complaints, safety story, sponsor inventory | Critical (access, distribution) |
| Sponsors (beverage, telecom, fintech brands) | Branded rendezvous points, visibility at moment of need | Medium (revenue) |
| Venue security / medical | Density heatmaps, SOS pings | Medium (safety contracts) |
| Google Play review | Permission justification (Bluetooth, location) | Gate |

## 6. Business requirements
| ID | Requirement | Priority | Rationale |
|---|---|---|---|
| BR-01 | Must work with zero mobile data, Wi-Fi or internet | Must | Core premise |
| BR-02 | Must run on Android phones common in India (Android 8+, mid-range Xiaomi/Samsung/Vivo/Realme/OnePlus) | Must | Market coverage |
| BR-03 | A new user must be able to join a friend's group in under 60 seconds | Must | Adoption at the gate |
| BR-04 | Battery consumption must stay under 3% per hour with the app running | Must | 10-hour festival day |
| BR-05 | Position updates from group members must reach a user within 30 s when within relay range | Must | Usefulness |
| BR-06 | Codebook must cover ≥ 90% of real coordination messages (validated in college-fest test) | Should | Replaces chat |
| BR-07 | Must log delivery metrics and export them for post-event analysis | Must | Case study, O3 |
| BR-08 | Must not persist location history beyond the session without explicit opt-in | Must | Privacy, trust with organiser |
| BR-09 | Must be distributable as a sideloaded APK and via Play Store | Must | Testing then launch |
| BR-10 | Architecture must allow the protocol to be reimplemented on iOS and on ESP32 relay hardware | Should | v2 and totems |
| BR-11 | Must support anonymous, aggregated density heatmap generation | Could | Safety revenue line |
| BR-12 | Rendezvous points must be configurable per venue so sponsors can name them | Could | Sponsorship revenue line |

## 7. Business model (post-pilot)
Attendees are the users; organisers and sponsors are the customers.

| Revenue line | Mechanism | Indicative pricing |
|---|---|---|
| Event licence | Per-event or per-attendee licence to organisers (SDK or white-label) | ₹2–5 per ticket |
| Sponsorship | Branded POIs / rendezvous points, sponsor name in codebook | Sold via organiser's sponsorship team, rev-share |
| Crowd-safety analytics | Anonymised live density heatmap for security ops | Per-event safety package |
| Totem rental | Relay hardware + setup as a managed service | Per-event, per-node |
| Adjacent markets | Marathons, Kumbh Mela and religious gatherings, trekking, disaster response | Later |

Lolla 2027 runs **free** as the showcase.

## 8. Success metrics (Lolla 2027 pilot)
| Metric | Target |
|---|---|
| Installs among pilot cohort (crew + invited attendees) | ≥ 500 |
| Ping delivery rate within 60 s | ≥ 70% |
| Median time-to-reunion after a "Meet at" ping | ≤ 10 min |
| Battery drain | ≤ 3%/hour |
| App crash-free sessions | ≥ 98% |
| Qualitative: organiser willing to be a reference | Yes |

## 9. Constraints & assumptions
- **Assumption:** GPS is available outdoors at the venue with 10–30 m accuracy. Indoor stages will degrade this.
- **Assumption:** Attendees keep phones out and app in foreground for meaningful periods; the app runs a foreground service otherwise.
- **Constraint:** Android background BLE scanning is throttled; the app must use a foreground service with a persistent notification.
- **Constraint:** iPhones cannot participate in v1 (5–10% of likely attendees).
- **Constraint:** Organiser permission is required to place totems and to distribute to attendees at scale.
- **Constraint:** Play Store review requires justification for Bluetooth and location permissions; allow ≥ 4 weeks buffer.
- **Budget (indicative):** ₹0 software (founder-built with Claude Code); ₹25k for test phones/accessories; ₹1.5–3 lakh for 30–60 totems if approved; ₹8k Play/Apple developer accounts.

## 10. Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| BLE channel congestion at 60k density collapses relaying | Medium | High | Geo-routed relay (not flooding), adaptive intervals, totems, staged testing at cricket/religious crowds before Lolla |
| Organiser declines partnership | Medium | High | Run independently with QR posters + totems in a small zone; still yields data |
| Low install rate → mesh never forms | High (standalone) | High | Crew/volunteer seeding, gate QR, push for inclusion in official app |
| Android OEM battery killers stop the service | Medium | Medium | Foreground service, OEM-specific "don't optimise" prompts, tested on Xiaomi/Vivo |
| Play Store rejection | Medium | Medium | Submit 4+ weeks early; avoid background location permission in v1 |
| Privacy backlash | Low | High | Group-only sharing, no persistence, no server, open protocol |
| GPS drift makes dots misleading | Medium | Medium | Show accuracy radius; rely on POI-level rendezvous rather than exact position |

## 11. Timeline
| When | Milestone |
|---|---|
| Oct 2026 | Phase 0–2: two-phone prototype with map and codebook |
| Nov 2026 | Phase 3–4: multi-hop relay, rendezvous, lighthouse; 3–5 phone tests |
| Dec 2026 | Phase 5: field-test tooling; first dense-crowd test (cricket match / religious procession) |
| Jan 2027 | College fest test (50–100 phones); organiser pitch with data |
| Early Feb 2027 | Play Store submission; totem firmware if approved |
| Late Feb 2027 | Crew-only dry run at venue build-up |
| Q1 2027 (event) | Lolla pilot |
| Event + 30 days | Case study; outreach to second customer |

## 12. Approval
| Role | Name | Date |
|---|---|---|
| Product owner | | |
| Technical lead | | |
| Organiser sponsor (if any) | | |
