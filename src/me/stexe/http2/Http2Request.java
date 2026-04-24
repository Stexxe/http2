package me.stexe.http2;

import java.net.URL;

public record Http2Request(
     HttpMethod method,
     URL url,
     byte[] body
) {
    public Http2Request(HttpMethod method, URL url) {
        this(method, url, new byte[0]);
    }
}
