import org.junit.jupiter.api.extension.*;

import java.io.IOException;
import java.util.Scanner;
import static org.junit.jupiter.api.Assertions.fail;

public class H2ServerExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private Process server;
    private int port;
    private Thread stderrThread;

    void start(String script) throws IOException {
        server = new ProcessBuilder("test-data/.venv/bin/python3", "test-data/%s".formatted(script)).start();

        stderrThread = Thread.ofVirtual().start(() -> {
            try {
                server.getErrorStream().transferTo(System.err);
            } catch (IOException e) {
                fail(e);
            }
        });

        var scanner = new Scanner(server.getInputStream());
        port = Integer.parseInt(scanner.nextLine());
    }

    public int port() {
        return port;
    }

    @Override public void beforeEach(ExtensionContext ctx) throws IOException {
        ctx.getStore(ExtensionContext.Namespace.GLOBAL).put(H2ServerExtension.class, this);
        var annotation = ctx.getRequiredTestMethod().getAnnotation(H2Server.class);
        if (annotation != null) {
            start(annotation.script());
        }
    }
    @Override public void afterEach(ExtensionContext ctx) throws InterruptedException {
        if (server != null) server.destroy();
        if (stderrThread != null) stderrThread.join();
    }

    @Override
    public boolean supportsParameter(ParameterContext param, ExtensionContext ctx) {
        return param.getParameter().getType() == H2ServerExtension.class;
    }

    @Override
    public Object resolveParameter(ParameterContext param, ExtensionContext ctx) {
        return ctx.getStore(ExtensionContext.Namespace.GLOBAL).get(H2ServerExtension.class);
    }
}

