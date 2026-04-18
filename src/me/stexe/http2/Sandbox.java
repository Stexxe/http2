void main() throws IOException, InterruptedException {
    Thread logger = Thread.ofVirtual().start(() -> {
        while (true) {
            try {
                System.out.println(logQueue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    });

    var port = 9090;
    byte[] preface = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    try (Socket socket = new Socket("localhost", port)) {
        var out = socket.getOutputStream();
        var in = socket.getInputStream();

        AtomicLong maxConcurrentStreams = new AtomicLong(Long.MAX_VALUE);
        var settingsAckReceived = new CountDownLatch(1);
        var ctx = new Context(out, new AtomicInteger(0));

        var writer = Thread.ofVirtual().start(() -> {
            try {
                out.write(preface);

                var emptySettings = new Frame(0, FrameType.Settings, 0, 0, new byte[0]);
                writeFrame(out, emptySettings);

                out.flush();

                settingsAckReceived.await();

                var headers = new ArrayList<HpackHeader>();
                headers.add(new IndexedHeader(StaticHeaders.MethodGET.index));
                headers.add(new IndexedHeader(StaticHeaders.PathSlash.index));
                headers.add(new IndexedHeader(StaticHeaders.SchemeHttp.index));
                headers.add(new LiteralIndexedHeader(StaticHeaders.Authority.index, "localhost:9090", false));

                writeHeaders(out, headers);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        var reader = Thread.ofVirtual().start(() -> {
            while (true) {
                try {
                    var frame = readFrame(in);

                    if (frame.type == FrameType.Settings) {
                        var settings = readSettings(ctx, frame);

                        if (settings.entries.containsKey(SettingId.MaxConcurrentStreams.id)) {
                            maxConcurrentStreams.set(settings.get(SettingId.MaxConcurrentStreams));
                        }

                        if ((frame.flags & ACK) > 0) {
                            settingsAckReceived.countDown();
                        }
                    } else if (frame.type == FrameType.Headers) {
                        var headers = readHeaders(ctx, frame);

                    } else if (frame.type == FrameType.GoAway) {
                        var goaway = readGoAway(ctx, frame);

                        System.out.printf("Server sent GOAWAY: %s\n", goaway.code.name());
                        break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        writer.join();
        reader.join();
    }
}

BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();

void log(String msg) {
    logQueue.add(msg);
}

enum StaticHeaders {
    Authority(1),
    MethodGET(2),
    MethodPOST(3),
    PathSlash(4),
    PathIndexHtml(5),
    SchemeHttp(6),
    SchemeHttps(7),
    Status200(8),
    Status204(9),
    Status206(10),
    Status304(11),
    Status400(12),
    Status404(13),
    Status500(14),
    AcceptCharset(15),
    AcceptEncodingGzipDeflate(16),
    AcceptLanguage(17),
    AcceptRanges(18),
    Accept(19),
    AccessControlAllowOrigin(20),
    Age(21),
    Allow(22),
    Authorization(23),
    CacheControl(24),
    ContentDisposition(25),
    ContentEncoding(26),
    ContentLanguage(27),
    ContentLength(28),
    ContentLocation(29),
    ContentRange(30),
    ContentType(31),
    Cookie(32),
    Date(33),
    Etag(34),
    Expect(35),
    Expires(36),
    From(37),
    Host(38),
    IfMatch(39),
    IfModifiedSince(40),
    IfNoneMatch(41),
    IfRange(42),
    IfUnmodifiedSince(43),
    LastModified(44),
    Link(45),
    Location(46),
    MaxForwards(47),
    ProxyAuthenticate(48),
    ProxyAuthorization(49),
    Range(50),
    Referer(51),
    Refresh(52),
    RetryAfter(53),
    Server(54),
    SetCookie(55),
    StrictTransportSecurity(56),
    TransferEncoding(57),
    UserAgent(58),
    Vary(59),
    Via(60),
    WwwAuthenticate(61);

    final int index;
    StaticHeaders(int i) {
        index = i;
    }
}

final int ACK = 0x1;
final int END_STREAM = 0x1;
final int END_HEADERS = 0x4;
final int PADDED = 0x8;
final int PRIORITY = 0x20;

enum FrameType {
    Data(0x0),
    Headers(0x1),
    Priority(0x2),
    RstStream(0x3),
    Settings(0x4),
    PushPromise(0x5),
    Ping(0x6),
    GoAway(0x7),
    WindowUpdate(0x8),
    Continuation(0x9);

    final int id;
    FrameType(int id) { this.id = id; }
}

enum SettingId {
    HeaderTableSize(0x1),
    EnablePush(0x2),
    MaxConcurrentStreams(0x3),
    InitialWindowSize(0x4),
    MaxFrameSize(0x5),
    MaxHeaderListSize(0x6),
    EnableConnectProtocol(0x8),
    NoRfc7540Priorities(0x9);

    final int id;
    SettingId(int id) { this.id = id; }

    static final Set<Integer> ALL = Arrays.stream(values()).map(s -> s.id).collect(Collectors.toSet());
}

enum ErrorCode {
    NoError(0x0),
    ProtocolError(0x1),
    InternalError(0x2),
    FlowControlError(0x3),
    SettingsTimeout(0x4),
    StreamClosed(0x5),
    FrameSizeError(0x6),
    RefusedStream(0x7),
    Cancel(0x8),
    CompressionError(0x9),
    ConnectError(0xa),
    EnhanceYourCalm(0xb),
    InadequateSecurity(0xc),
    Http11Required(0xd);

    final int id;
    ErrorCode(int id) { this.id = id; }
}

record Frame(
    int length,
    FrameType type,
    int flags,
    int streamId,
    byte[] payload
) {}

record Settings(
    Map<Integer, Long> entries
) {
    long get(SettingId id) {
        return entries.getOrDefault(id.id, -1L);
    }
}

record GoAway(
    int lastStreamId,
    ErrorCode code
) {}

record Context(
    OutputStream out,
    AtomicInteger lastStreamId
) {}

Frame readFrame(InputStream input) throws IOException {
    byte[] header = input.readNBytes(9);
    int length = ((header[0] & 0xff) << 16) | ((header[1] & 0xff) << 8) | (header[2] & 0xff);

    FrameType type = switch (header[3]) {
        case 0x0 -> FrameType.Data;
        case 0x1 -> FrameType.Headers;
        case 0x4 -> FrameType.Settings;
        case 0x6 -> FrameType.Ping;
        case 0x7 -> FrameType.GoAway;
        case 0x8 -> FrameType.WindowUpdate;
        case 0x9 -> FrameType.Continuation;
        default -> throw new IllegalArgumentException("Unknown type %x".formatted(header[3])); // TODO: Send Goaway
    };

    byte flags = header[4];

    int streamId = ((header[5] & 0x7f) << 24) | ((header[6] & 0xff) << 16) | ((header[7] & 0xff) << 8) | (header[8] & 0xff);

    byte[] payload = input.readNBytes(length);

    return new Frame(length, type, flags, streamId, payload);
}

GoAway readGoAway(Context ctx, Frame frame) throws IOException {
    var payload = frame.payload;

    if (payload.length < 8) {
        writeGoAway(ctx.out, ctx.lastStreamId.intValue(), ErrorCode.FrameSizeError);
        throw new ProtocolException("Length must be 8 for GOAWAY frame, got %d bytes".formatted(frame.length));
    }

    int lastStreamId = ((payload[0] & 0x7f) << 24) | ((payload[1] & 0xff) << 16) | ((payload[2] & 0xff) << 8) | (payload[3] & 0xff);
    long longCode = ((long) (payload[4] & 0xff) << 24) | ((payload[5] & 0xff) << 16) | ((payload[6] & 0xff) << 8) | (payload[7] & 0xff);

    var code = Arrays.stream(ErrorCode.values()).filter(c -> c.id == longCode).findFirst();

    return new GoAway(lastStreamId, code.orElse(ErrorCode.InternalError));
}

Settings readSettings(Context ctx, Frame frame) throws IOException {
    if ((frame.flags & ACK) > 0 && frame.length != 0) {
        writeGoAway(ctx.out, ctx.lastStreamId.intValue(), ErrorCode.ProtocolError);
        throw new ProtocolException("ACK flag is set so payload must be empty, got %d bytes".formatted(frame.length));
    }

    if (frame.length % 6 != 0) {
        writeGoAway(ctx.out, ctx.lastStreamId.intValue(), ErrorCode.FrameSizeError);
        throw new ProtocolException("Length is not multiple of 6, got %d bytes".formatted(frame.length));
    }

    var payload = frame.payload;
    var entries = new HashMap<Integer, Long>();
    for (int i = 0; i < frame.length; i += 6) {
        int id = ((payload[i] & 0xff) << 8) | (payload[i + 1] & 0xff);
        long value = ((long) (payload[i + 2] & 0xff) << 24) |
                ((payload[i+3] & 0xff) << 16) |
                ((payload[i+4] & 0xff) << 8) |
                (payload[i+5] & 0xff);


        if (SettingId.ALL.contains(id)) {
            entries.put(id, value);
        }
    }

    return new Settings(entries);
}

void writeFrame(OutputStream out, Frame frame) throws IOException {
    var header = new byte[9];
    header[0] = (byte) ((frame.length >> 16) & 0xff);
    header[1] = (byte) ((frame.length >> 8) & 0xff);
    header[2] = (byte) (frame.length & 0xff);

    header[3] = (byte) (frame.type.id & 0xff);

    header[4] = (byte) (frame.flags & 0xff);

    header[5] = (byte) ((frame.streamId >> 24) & 0x7f);
    header[6] = (byte) ((frame.streamId >> 16) & 0xff);
    header[7] = (byte) ((frame.streamId >> 8) & 0xff);
    header[8] = (byte) (frame.streamId & 0xff);

    out.write(header);
    out.write(frame.payload);
    out.flush();
}

void writeGoAway(OutputStream out, int lastStreamId, ErrorCode code) throws IOException {
    var payload = new byte[8];
    payload[0] = (byte) (lastStreamId >> 24);
    payload[1] = (byte) (lastStreamId >> 16);
    payload[2] = (byte) (lastStreamId >> 8);
    payload[3] = (byte) lastStreamId;

    payload[4] = (byte) (code.id >> 24);
    payload[5] = (byte) (code.id >> 16);
    payload[6] = (byte) (code.id >> 8);
    payload[7] = (byte) code.id;

    writeFrame(out, new Frame(8, FrameType.GoAway, 0, 0, payload));
}

sealed interface HpackHeader permits IndexedHeader, LiteralIndexedHeader, LiteralHeader {}
record IndexedHeader(int index) implements HpackHeader {}
record LiteralIndexedHeader(int index, String value, boolean indexing) implements HpackHeader {}
record LiteralHeader(String name, String value, boolean indexing) implements HpackHeader {}

void writeHeaders(OutputStream out, List<HpackHeader> headers) throws IOException {
    var headersStream = new ByteArrayOutputStream();

    for (var h: headers) {
        switch (h) {
            case IndexedHeader ih -> {
                headersStream.write((ih.index & 0xff) | 0x80); // TODO: Fix for indexes 127+
            }
            case LiteralIndexedHeader lih -> {
                if (lih.indexing) {
                    throw new IllegalStateException("Indexing for LiteralIndexedHeader not implemented");
                }

                if (lih.index > 0xf) {
                    throw new IllegalStateException("Multibyte index for LiteralIndexedHeader not implemented");
                }

                headersStream.write((byte) lih.index);

                var strBytes = lih.value.getBytes(StandardCharsets.US_ASCII);

                if (strBytes.length > 0x7f) {
                    throw new IllegalStateException("String size more than 127 byte for LiteralIndexedHeader not implemented");
                }

                headersStream.write(strBytes.length & 0x7f);
                headersStream.write(strBytes);
            }
            default -> {
                throw new IllegalStateException("Header type not implemented");
            }
        }
    }

    var headerBlock = headersStream.toByteArray();
    // TODO: Fix stream ID and flags
    writeFrame(out, new Frame(headerBlock.length, FrameType.Headers, END_STREAM | END_HEADERS, 1, headerBlock));
}

record Headers(
    List<HpackHeader> headers
) {}

Headers readHeaders(Context ctx, Frame frame) {
    // TODO: Implement padding

    int i = 0;
    var headers = new ArrayList<HpackHeader>();
    while (i < frame.length) {
        if ((frame.payload[i] & 0x80) != 0) { // Indexed
            int index = frame.payload[i] & 0x7f;
            assert(index <= StaticHeaders.values().length); // TODO: Dynamic table
            headers.add(new IndexedHeader(index));
            i++;
        } else if ((frame.payload[i] >> 6) == 0x01) { // Literal + indexing
            int index = frame.payload[i] & 0x3f;
            assert(index <= StaticHeaders.values().length); // TODO: Dynamic table

            boolean huffmanEncoded = (frame.payload[i + 1] & 0x80) != 0;
            int valueLen = frame.payload[i + 1] & 0x7f;

            var strEnd = i + 1 + valueLen;
        }
    }

    return new Headers(headers);
}