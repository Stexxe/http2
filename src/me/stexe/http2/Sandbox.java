import me.stexe.http2.Http2Client;
import me.stexe.http2.Http2Request;
import me.stexe.http2.HttpMethod;

void main() throws IOException, InterruptedException, URISyntaxException, ExecutionException {
    try (var client = Http2Client.connectH2c("localhost", 9090)) {
        var response = client.send(
            new Http2Request(HttpMethod.GET, new URI("http://localhost:9090/").toURL())
        );

        System.out.printf("STATUS: %d%n", response.status());
        System.out.println("HEADERS:");
        for (var h : response.headers()) {
            System.out.printf("%s: %s%n", h.name(), h.value());
        }
        System.out.println("BODY:");
        System.out.println(new String(response.body()));
    }
}