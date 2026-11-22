package ch.omidahmadi.vpscontroller.check;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpProbeTest {

    private final HttpProbe probe = new HttpProbe();
    private final StatusRanges defaultAccept = StatusRanges.parse(List.of("200-399"));
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void twoHundredIsOkAndMeasuresLatency() {
        String url = start(200, 0);

        ProbeResult result = probe.check(url, Duration.ofSeconds(2), defaultAccept);

        assertThat(result.ok()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.latencyMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(result.error()).isNull();
    }

    @Test
    void fiveHundredIsAnUnexpectedStatusWithGermanError() {
        String url = start(500, 0);

        ProbeResult result = probe.check(url, Duration.ofSeconds(2), defaultAccept);

        assertThat(result.ok()).isFalse();
        assertThat(result.statusCode()).isEqualTo(500);
        assertThat(result.error()).isEqualTo("HTTP 500 (erwartet 200-399)");
    }

    @Test
    void acceptedFourOhOneCountsAsOk() {
        String url = start(401, 0);

        ProbeResult result = probe.check(url, Duration.ofSeconds(2), StatusRanges.parse(List.of("200-399", "401")));

        assertThat(result.ok()).isTrue();
        assertThat(result.statusCode()).isEqualTo(401);
        assertThat(result.error()).isNull();
    }

    @Test
    void slowServerBecomesATimeout() {
        String url = start(200, 1000);

        ProbeResult result = probe.check(url, Duration.ofMillis(300), defaultAccept);

        assertThat(result.ok()).isFalse();
        assertThat(result.statusCode()).isNull();
        assertThat(result.error()).isEqualTo("Zeitüberschreitung nach 300 ms");
    }

    @Test
    void closedPortBecomesAConnectionError() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }

        ProbeResult result = probe.check("http://127.0.0.1:" + port + "/", Duration.ofSeconds(2), defaultAccept);

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).startsWith("Keine Verbindung:");
    }

    @Test
    void numberOfRequestsIsCounted() {
        AtomicInteger hits = new AtomicInteger();
        String url = start(200, 0, hits);

        probe.check(url, Duration.ofSeconds(2), defaultAccept);

        assertThat(hits.get()).isEqualTo(1);
    }

    private String start(int status, long delayMs) {
        return start(status, delayMs, new AtomicInteger());
    }

    private String start(int status, long delayMs, AtomicInteger hits) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                hits.incrementAndGet();
                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                byte[] body = "ok".getBytes();
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
