package me.stexe.http2;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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

enum Flag {
    ACK(0x1),
    END_STREAM(0x1),
    END_HEADERS(0x4),
    PADDED(0x8),
    PRIORITY(0x20);

    final int id;
    Flag(int id) { this.id = id; }
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

enum StaticHeader {
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
    StaticHeader(int i) {
        index = i;
    }

    static StaticHeader of(int index) {
        return Arrays.stream(StaticHeader.values()).filter(sh -> sh.index == index).findFirst().orElseThrow();
    }
}