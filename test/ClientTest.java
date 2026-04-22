import me.stexe.http2.Http2Client;
import me.stexe.http2.Http2Request;
import me.stexe.http2.HttpMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ClientTest {

    @Test
    @Timeout(5)
    void noContent() throws IOException, URISyntaxException, ExecutionException, InterruptedException {
        var server = new ProcessBuilder("test-data/.venv/bin/python3", "test-data/no-content.py").start();

        var stderrThread = Thread.ofVirtual().start(() -> {
            try {
                server.getErrorStream().transferTo(System.err);
            } catch (IOException e) {
                fail(e);
            }
        });

        var scanner = new Scanner(server.getInputStream());

        try {
            var port = Integer.parseInt(scanner.nextLine());

            try (var client = Http2Client.connectH2c("localhost", port)) {
                var response = client.send(
                    new Http2Request(HttpMethod.GET, new URI("http://localhost:%d/".formatted(port)).toURL())
                );

                assertEquals(204, response.status());
                assertEquals(0, response.body().length);
            }
        } finally {
            server.destroy();
            stderrThread.join();
        }
    }
}
