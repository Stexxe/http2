import me.stexe.http2.Http2Client;
import me.stexe.http2.Http2Request;
import me.stexe.http2.HttpMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Execution(ExecutionMode.CONCURRENT)
@ExtendWith(H2ServerExtension.class)
public class ClientTest {

    @Test
    @H2Server(script = "no-content.py")
    @Timeout(5)
    void noContent(H2ServerExtension server) throws IOException, URISyntaxException, ExecutionException, InterruptedException {
        try (var client = Http2Client.connectH2c("localhost", server.port())) {
            var response = client.send(
                new Http2Request(HttpMethod.GET, new URI("http://localhost:%d/".formatted(server.port())).toURL())
            );

            assertEquals(204, response.status());
            assertEquals(0, response.body().length);
        }
    }

    @Test
    @H2Server(script = "text-content.py")
    @Timeout(5)
    void textContent(H2ServerExtension server) throws IOException, URISyntaxException, ExecutionException, InterruptedException {
        try (var client = Http2Client.connectH2c("localhost", server.port())) {
            var response = client.send(
                new Http2Request(HttpMethod.GET, new URI("http://localhost:%d/".formatted(server.port())).toURL())
            );

            assertEquals(200, response.status());
            assertEquals("hello", new String(response.body()));
        }
    }

    @Test
    @H2Server(script = "post-text.py")
    void postText(H2ServerExtension server) throws IOException, URISyntaxException, ExecutionException, InterruptedException {
        try (var client = Http2Client.connectH2c("localhost", server.port())) {
            var response = client.send(
                new Http2Request(
                    HttpMethod.POST,
                    new URI("http://localhost:%d/post".formatted(server.port())).toURL(),
                    "post body".getBytes()
                )
            );

            assertEquals(200, response.status());
            assertEquals("client sent: post body", new String(response.body()));
        }
    }
}
