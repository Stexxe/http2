package me.stexe.http2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Http2Client implements AutoCloseable {
    private final Socket socket;
    private final OutputStream out;
    private final InputStream in;

//    private AtomicLong maxConcurrentStreams;
//    private CountDownLatch settingsAckReceived;
//    private CountDownLatch settingsReceived;
//    private Context ctx;

    private final BlockingQueue<PendingRequest> writeQueue;
//    private AtomicInteger lastStreamId;
//    private ConcurrentMap<Integer, CompletableFuture<Http2Response>> pendingFutures;
//    private ConcurrentMap<Integer, Http2Response.Builder> pendingResponses;

    private final Thread writer;
    private final Thread reader;

    private Http2Client(Socket socket, Thread writer, Thread reader, BlockingQueue<PendingRequest> writeQueue) throws IOException {
        this.socket = socket;
        out = socket.getOutputStream();
        in = socket.getInputStream();
        this.writer = writer;
        this.reader = reader;
        this.writeQueue = writeQueue;
    }

    public static Http2Client connectH2c(String host, int port) throws IOException {
        var socket = new Socket(host, port);
        var out = socket.getOutputStream();
        var in = socket.getInputStream();

        AtomicLong maxConcurrentStreams = new AtomicLong(Long.MAX_VALUE);
        var settingsAckReceived = new CountDownLatch(1);
        var settingsReceived = new CountDownLatch(1);
        var ctx = new Context(out, new AtomicInteger(0));
        byte[] preface = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

        var writeQueue = new LinkedBlockingQueue<PendingRequest>();
        var lastStreamId = new AtomicInteger(1);
        var pendingFutures = new ConcurrentHashMap<Integer, CompletableFuture<Http2Response>>();
        var pendingResponses = new ConcurrentHashMap<Integer, Http2Response.Builder>();

        var writer = Thread.ofVirtual().start(() -> {
            try {
                out.write(preface);
                var emptySettings = new Frame(0, FrameType.Settings, 0, 0, new byte[0]);
                writeFrame(out, emptySettings);
                out.flush();

                settingsReceived.await();

                writeFrame(out, new Frame(0, FrameType.Settings, Flag.ACK.id, 0, new byte[0]));

                settingsAckReceived.await();

                while (true) {
                    try {
                        var pending = writeQueue.take();
                        var streamId = lastStreamId.getAndAdd(2);
                        pendingFutures.put(streamId, pending.future());
                        pendingResponses.put(streamId, new Http2Response.Builder());

                        var request = pending.request();
                        var url = request.url();

                        var headers = new ArrayList<HpackHeader>();
                        assert(request.method() == HttpMethod.GET);
                        headers.add(new IndexedHeader(StaticHeader.MethodGET.index));

                        assert(Objects.equals(url.getPath(), "/"));
                        headers.add(new IndexedHeader(StaticHeader.PathSlash.index));

                        headers.add(new IndexedHeader(StaticHeader.SchemeHttp.index));

                        var hostPort = url.getPort() == -1 ? url.getHost() : "%s:%d".formatted(url.getHost(), url.getPort());
                        headers.add(new LiteralIndexedHeader(StaticHeader.Authority.index, hostPort, false));

                        writeHeaders(out, headers, streamId);
                    } catch (InterruptedException e) {
                        break;
                    } catch (IOException e) {
                        if (e instanceof SocketException) {
                            Thread.currentThread().interrupt();
                            break;
                        }

                        e.printStackTrace();
                        // TODO: Logging
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                // TODO: Logging
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        var reader = Thread.ofVirtual().start(() -> {
            while (true) {
                try {
                    var frame = readFrame(in);

                    if (frame.type() == FrameType.Settings) {
                        var settings = readSettings(ctx, frame);

                        if (settings.entries().containsKey(SettingId.MaxConcurrentStreams.id)) {
                            maxConcurrentStreams.set(settings.get(SettingId.MaxConcurrentStreams));
                        }

                        settingsReceived.countDown();

                        if ((frame.flags() & Flag.ACK.id) > 0) {
                            settingsAckReceived.countDown();
                        }
                    } else if (frame.type() == FrameType.Headers) {
                        var headers = readHeaders(ctx, frame);

                        var responseBuilder = pendingResponses.get(frame.streamId());

                        if (responseBuilder != null) {
                            for (var h: headers.headers()) {
                                switch (h) {
                                    case IndexedHeader ih -> {
                                        var staticHeader = StaticHeader.of(ih.index);
                                        switch (staticHeader) {
                                            case StaticHeader.Status200 -> responseBuilder.setStatus(200);
                                            case StaticHeader.Status204 -> responseBuilder.setStatus(204);
                                            case StaticHeader.Status206 -> responseBuilder.setStatus(206);
                                            case StaticHeader.Status304 -> responseBuilder.setStatus(304);
                                            case StaticHeader.Status400 -> responseBuilder.setStatus(400);
                                            case StaticHeader.Status404 -> responseBuilder.setStatus(404);
                                            case StaticHeader.Status500 -> responseBuilder.setStatus(500);
                                        }

                                        // TODO: Assign other headers
                                    }
                                    case LiteralIndexedHeader lih -> {
                                        assert(!lih.indexing);
                                        var staticHeader = StaticHeader.of(lih.index);

                                        switch (staticHeader) {
                                            case StaticHeader.Server -> responseBuilder.addHeader("Server", lih.value);
                                            case StaticHeader.Date -> responseBuilder.addHeader("Date", lih.value);
                                            case StaticHeader.ContentType -> responseBuilder.addHeader("Content-Type", lih.value);
                                            case StaticHeader.ContentLength -> responseBuilder.addHeader("Content-Length", lih.value);
                                        }

                                        // TODO: Assign other headers
                                    }
                                    default -> {
                                        throw new IllegalStateException("Not implemented");
                                    }
                                }
                            }

                            if ((frame.flags() & Flag.END_STREAM.id) != 0) {
                                var future = pendingFutures.get(frame.streamId());

                                if (future != null) {
                                    future.complete(responseBuilder.build());
                                }
                            }
                        }
                    } else if (frame.type() == FrameType.Data) {
                        var data = readData(ctx, frame);

                        var responseBuilder = pendingResponses.get(frame.streamId());

                        if (responseBuilder != null) {
                            responseBuilder.bodyStream.write(data.body());

                            if ((frame.flags() & Flag.END_STREAM.id) > 0) {
                                var future = pendingFutures.get(frame.streamId());

                                if (future != null) {
                                    future.complete(responseBuilder.build());
                                }
                            }
                        }
                    } else if (frame.type() == FrameType.GoAway) {
                        var goaway = readGoAway(ctx, frame);

                        // TODO: Logging
                        System.out.printf("Server sent GOAWAY: %s\n", goaway.code().name());
                    } else {
                        throw new IllegalStateException("Frame type %s not implemented".formatted(frame.type().name()));
                    }
                } catch (IOException e) {
                    if (e instanceof SocketException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    e.printStackTrace();
                }
            }
        });

        return new Http2Client(socket, writer, reader, writeQueue);
    }

    public Http2Response send(Http2Request request) throws InterruptedException, ExecutionException {
        var future = new CompletableFuture<Http2Response>();
        var pending = new PendingRequest(request, future);
        writeQueue.put(pending);
        return future.get();
    }

    static Frame readFrame(InputStream input) throws IOException {
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

    static GoAway readGoAway(Context ctx, Frame frame) throws IOException {
        var payload = frame.payload();

        if (payload.length < 8) {
            writeGoAway(ctx.out(), ctx.lastStreamId().intValue(), ErrorCode.FrameSizeError);
            throw new ProtocolException("Length must be 8 for GOAWAY frame, got %d bytes".formatted(frame.length()));
        }

        int lastStreamId = ((payload[0] & 0x7f) << 24) | ((payload[1] & 0xff) << 16) | ((payload[2] & 0xff) << 8) | (payload[3] & 0xff);
        long longCode = ((long) (payload[4] & 0xff) << 24) | ((payload[5] & 0xff) << 16) | ((payload[6] & 0xff) << 8) | (payload[7] & 0xff);

        var code = Arrays.stream(ErrorCode.values()).filter(c -> c.id == longCode).findFirst();

        return new GoAway(lastStreamId, code.orElse(ErrorCode.InternalError));
    }

    static Settings readSettings(Context ctx, Frame frame) throws IOException {
        if ((frame.flags() & Flag.ACK.id) > 0 && frame.length() != 0) {
            writeGoAway(ctx.out(), ctx.lastStreamId().intValue(), ErrorCode.ProtocolError);
            throw new ProtocolException("ACK flag is set so payload must be empty, got %d bytes".formatted(frame.length()));
        }

        if (frame.length() % 6 != 0) {
            writeGoAway(ctx.out(), ctx.lastStreamId().intValue(), ErrorCode.FrameSizeError);
            throw new ProtocolException("Length is not multiple of 6, got %d bytes".formatted(frame.length()));
        }

        var payload = frame.payload();
        var entries = new HashMap<Integer, Long>();
        for (int i = 0; i < frame.length(); i += 6) {
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

    static void writeFrame(OutputStream out, Frame frame) throws IOException {
        var header = new byte[9];
        header[0] = (byte) ((frame.length() >> 16) & 0xff);
        header[1] = (byte) ((frame.length() >> 8) & 0xff);
        header[2] = (byte) (frame.length() & 0xff);

        header[3] = (byte) (frame.type().id & 0xff);

        header[4] = (byte) (frame.flags() & 0xff);

        header[5] = (byte) ((frame.streamId() >> 24) & 0x7f);
        header[6] = (byte) ((frame.streamId() >> 16) & 0xff);
        header[7] = (byte) ((frame.streamId() >> 8) & 0xff);
        header[8] = (byte) (frame.streamId() & 0xff);

        out.write(header);
        out.write(frame.payload());
        out.flush();
    }

    static void writeGoAway(OutputStream out, int lastStreamId, ErrorCode code) throws IOException {
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

    @Override
    public void close() {
        reader.interrupt();
        writer.interrupt();
        try {
            socket.close();
        } catch (IOException e) {
            // TODO: Logging
            e.printStackTrace();
        }
    }

    sealed interface HpackHeader permits IndexedHeader, LiteralIndexedHeader, LiteralHeader {}
    record IndexedHeader(int index) implements HpackHeader {}
    record LiteralIndexedHeader(int index, String value, boolean indexing) implements HpackHeader {}
    record LiteralHeader(String name, String value, boolean indexing) implements HpackHeader {}

    static void writeHeaders(OutputStream out, List<HpackHeader> headers, int streamId) throws IOException {
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
        // TODO: Fix flags
        writeFrame(out, new Frame(headerBlock.length, FrameType.Headers, Flag.END_STREAM.id | Flag.END_HEADERS.id, streamId, headerBlock));
    }

    static Headers readHeaders(Context ctx, Frame frame) {
        // TODO: Implement padding
        assert((frame.flags() & Flag.END_HEADERS.id) > 0);

        int i = 0;
        var headers = new ArrayList<HpackHeader>();
        while (i < frame.length()) {
            if ((frame.payload()[i] & 0x80) != 0) { // Indexed
                int index = frame.payload()[i] & 0x7f;
                assert(index <= StaticHeader.values().length); // TODO: Dynamic table
                headers.add(new IndexedHeader(index));
                i++;
            } else if ((frame.payload()[i] >> 6) == 0x01) { // Literal + indexing
                int index = frame.payload()[i] & 0x3f;
                assert(index <= StaticHeader.values().length); // TODO: Dynamic table

                boolean huffmanEncoded = (frame.payload()[i + 1] & 0x80) != 0;
                int valueLen = frame.payload()[i + 1] & 0x7f;

                var value = huffmanEncoded ?
                        new String(Huffman.decode(frame.payload(), i + 2, valueLen)) :
                        new String(frame.payload(), i + 2, valueLen, StandardCharsets.US_ASCII);

                headers.add(new LiteralIndexedHeader(index, value, false)); // TODO: Indexing
                i += 2 + valueLen;
            } else if ((frame.payload()[i] >> 4) == 0x0000) {
                var decodeRes = decodeInteger(frame.payload(), i, 4);
                var index = decodeRes[0];
                var bytesRead = decodeRes[1];

                var valueRes = decodeInteger(frame.payload(), i + bytesRead, 7);
                var valueLen = valueRes[0];
                var valueStart = i + bytesRead + valueRes[1];

                boolean huffmanEncoded = (frame.payload()[i + bytesRead] & 0x80) != 0;

                var value = huffmanEncoded ?
                        new String(Huffman.decode(frame.payload(), valueStart, valueLen)) :
                        new String(frame.payload(), valueStart, valueLen, StandardCharsets.US_ASCII);

                headers.add(new LiteralIndexedHeader(index, value, false));
                i = valueStart + valueLen;
            } else {
                throw new IllegalStateException("Not Implemented");
            }
        }

        return new Headers(headers);
    }

    static Data readData(Context ctx, Frame frame) {
        // TODO: Padding
        assert((frame.flags() & Flag.PADDED.id) == 0);

        byte[] body = Arrays.copyOfRange(frame.payload(), 0, frame.length());
        return new Data(body);
    }

    // First -> number, Second -> bytes read
    static int[] decodeInteger(byte[] bytes, int start, int numBits) {
        int mask = (1 << numBits) - 1;
        long num = bytes[start] & mask;
        int i = start + 1;

        if (num == mask) { // Continuation
            long res = 0;
            int sh = 0;

            while (true) {
                res |= (long) (bytes[i] & 0x7f) << (sh * 7);
                if ((bytes[i] & 0x80) == 0) break;
                i++;
                sh++;
            }

            i++;
            num += res;
        }

        if (num > Integer.MAX_VALUE) {
            throw new IllegalStateException("Integer overflow");
        }

        return new int[] {(int) num, i - start};
    }
}

record Context(
    OutputStream out,
    AtomicInteger lastStreamId
) {}

record Frame(
    int length,
    FrameType type,
    int flags,
    int streamId,
    byte[] payload
) {}

record Data(
    byte[] body
) {}

record Headers(
    List<Http2Client.HpackHeader> headers
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

record PendingRequest(Http2Request request, CompletableFuture<Http2Response> future) {}