# Kickoff prompt for Claude Code

Paste this as your first message in Claude Code, from the repo root:

```
Read CLAUDE.md and every file in docs/ before doing anything.

Then:
1. Summarise, in your own words, what Firefly is and the three non-negotiable technical rules.
2. Restate Phase 0 exit criteria from docs/ROADMAP.md.
3. List the files you will create for Phase 0.
4. Wait for my "go" before writing code.

When I say go, implement Phase 0 only, run ./gradlew test, and stop. Do not begin Phase 1.
```

Follow-up prompts per phase:

```
Phase 1: restate exit criteria, list files, wait for go. Start with core/protocol/PacketCodec.kt and its round-trip tests for all four packet types using the worked example in docs/PROTOCOL.md §8 as a test vector.
```

```
Phase 3: before writing RelayPolicy, write the test table first — one test per rule in docs/PROTOCOL.md §5 — then implement until green.
```

Useful mid-phase prompts:
- "Show me the current advertising payload as hex and verify it against PROTOCOL.md."
- "Add a debug toggle to Stats that prints every received packet to Logcat with RSSI."
- "Check the manifest has no INTERNET permission and no library is pulling one in (`./gradlew :app:dependencies`)."
- "Which OEM battery settings do we prompt for? Compare with docs/TESTING.md."
