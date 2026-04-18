# HTTP/2 Client

A from-scratch HTTP/2 (h2c) client implementation in Java 25, built to understand the protocol internals.

## Goals

- Learn HTTP/2 framing, HPACK, and stream multiplexing by implementing them manually
- No third-party HTTP/2 libraries — only the JDK
- Uses `Socket` with virtual threads (Java 21+)

## Running a local server

```bash
brew install nghttp2
nghttpd 9090 --no-tls
```

## Current state

- Connection preface (magic string + empty SETTINGS frame)
- Frame reading and writing (`readFrame`, `writeFrame`)
- SETTINGS parsing and validation
- GOAWAY sending and parsing
- HPACK encoding for simple GET requests (static table only, no Huffman)
- HEADERS frame writing
- Reader loop handling Settings / GoAway / Headers frames
- Writer awaits SETTINGS ACK via `CountDownLatch` before sending request

## Not yet implemented

- `readHeaders` — HPACK decoding (static table lookup, Huffman decoding)
- Stream ID management (currently hardcoded to 1)
- DATA frame reading (response body)
- CONTINUATION frame handling
- SETTINGS ACK sending after receiving server SETTINGS
- Flow control (WINDOW_UPDATE)
- HPACK dynamic table
- HPACK literal with indexing on write path
- Multibyte integer encoding for indices > 15
