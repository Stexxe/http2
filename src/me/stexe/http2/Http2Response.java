package me.stexe.http2;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public record Http2Response(
    int status,
    List<Header> headers,
    byte[] body
) {

    public static class Builder {
        private int status;
        public final ArrayList<Header> headers;
        public final ByteArrayOutputStream bodyStream;

        public Builder() {
            status = 0;
            headers = new ArrayList<>();
            bodyStream = new ByteArrayOutputStream();
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public void addHeader(String name, String value) {
            headers.add(new Header(name, value));
        }

        public Http2Response build() {
            return new Http2Response(status, headers, bodyStream.toByteArray());
        }
    }
}
