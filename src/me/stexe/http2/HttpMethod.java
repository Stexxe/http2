package me.stexe.http2;

public enum HttpMethod {
    GET("GET"),
    POST("GET");

    final String name;
    HttpMethod(String name) {
        this.name = name;
    }
}
