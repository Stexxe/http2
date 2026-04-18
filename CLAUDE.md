# CLAUDE.md

## Project

From-scratch HTTP/2 (h2c) client in Java 25. Single source file: `src/me/stexe/http2/Sandbox.java`. Uses Java implicit classes (no explicit class declaration).

## How to assist

- Do not share code unless explicitly asked
- Do not ask the user if they are ready to proceed — the user guides the conversation
- Answer questions about the HTTP/2 protocol, HPACK, and Java APIs concisely
- When asked to check or verify code, read the file first and report issues

## Implementation state

### Done
- Client connection preface: 24-byte magic string + empty SETTINGS frame
- `writeFrame` / `readFrame` — 9-byte frame header encoding/decoding
- `readSettings` — 6-byte entry parsing, ACK+length validation, divisibility check
- `writeGoAway` / `readGoAway` — GOAWAY frame with error code
- `writeHeaders` — HPACK encoding for static table (IndexedHeader, LiteralIndexedHeader without indexing, strings ≤127 bytes)
- Reader loop handles Settings / GoAway / Headers frames
- `CountDownLatch` coordinates writer thread to wait for SETTINGS ACK before sending HEADERS

### Known TODOs (marked in code)
- `readFrame`: unknown frame type should send GOAWAY instead of throwing
- `writeHeaders`: IndexedHeader encoding broken for indices 128+
- `writeHeaders`: `LiteralIndexedHeader` with `indexing=true` not implemented
- `writeHeaders`: multibyte index (>15) not implemented
- `writeHeaders`: stream ID hardcoded to 1
- `readHeaders`: not implemented — needs HPACK decoding, Huffman support, PADDED flag handling

### Next steps
1. `readHeaders` — parse HPACK block from HEADERS frame
   - Check PADDED flag, skip pad length byte and trailing pad bytes
   - Bit patterns: `1xxxxxxx` indexed, `01xxxxxx` literal+indexing, `0000xxxx` literal no-index, `0001xxxx` literal never-index
   - String decoding: H bit (Huffman) + 7-bit length; Huffman decoding required on read path
2. Send SETTINGS ACK after receiving server SETTINGS
3. Stream ID management — AtomicInteger, client streams odd, starting at 1
4. Read DATA frames (response body)
5. Handle CONTINUATION frames

## Key design decisions
- `Socket` over `SocketChannel` — blocking I/O is fine with virtual threads
- Two virtual threads per connection: writer and reader
- `BlockingQueue<String>` logger with dedicated virtual thread
- `Context` record holds `OutputStream` and `AtomicInteger lastStreamId`
- `HpackHeader` sealed interface: `IndexedHeader`, `LiteralIndexedHeader`, `LiteralHeader`
- All frame flag constants defined as `final int`: ACK=0x1, END_STREAM=0x1, END_HEADERS=0x4, PADDED=0x8, PRIORITY=0x20
- Settings values stored as `Map<Integer, Long>` (raw integer keys to preserve unknown IDs)
- `StaticHeaders` enum: all 62 HPACK static table entries, 1-based index
- `SettingId` enum: RFC 7540 + RFC 8441 (ENABLE_CONNECT_PROTOCOL) + RFC 9218 (NO_RFC7540_PRIORITIES)
