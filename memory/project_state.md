---
name: HTTP/2 client implementation state
description: Current progress and next steps for the from-scratch HTTP/2 client in Java 25
type: project
---

Current work is on `readHeaders` — HPACK decoding of the HEADERS frame payload.

**Why:** Building a from-scratch HTTP/2 (h2c) client in a single Java file using Java 25 implicit classes.

**How to apply:** Use this to orient quickly on what's done and what's next without re-reading the full file.

## Recently completed
- Fixed `StaticHeaders` enum: removed bogus `Status403(13)`, shifted all subsequent indices down by 1, `WwwAuthenticate` now correctly at 61 (was 62)
- `readHeaders` stub is being filled in — user has added partial literal+indexing branch (lines ~406-414)

## In progress
- Huffman decoder — tree-based approach agreed on; not yet written
- `readHeaders` — PADDED/PRIORITY prefix skipping not yet done; literal branches incomplete; indexed branch has a bug (wrong bit check)

## Next steps
1. Implement Huffman tree builder + decoder (RFC 7541 Appendix B code table)
2. Complete `readHeaders`: fix indexed check, finish literal branches, handle PADDED/PRIORITY flags
3. Send SETTINGS ACK after receiving server SETTINGS
4. Stream ID management (AtomicInteger, odd client IDs starting at 1)
5. Read DATA frames
6. Handle CONTINUATION frames
