package ch.omidahmadi.vpscontroller.check;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HeartbeatTest {

    private static final Instant NOW = Instant.parse("2026-11-28T07:12:04Z");

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void unconfiguredHeartbeatDoesNothingAndReportsNoSuccess() {
        Heartbeat heartbeat = new Heartbeat(RestClient.builder().build(), null, new MutableClock(NOW));

        heartbeat.pulse();

        assertThat(heartbeat.configured()).isFalse();
        assertThat(heartbeat.lastOk()).isNull();
    }

    @Test
    void successfulPingRecordsTheInstantOfTheLastSuccess() {
        Heartbeat heartbeat = heartbeat(NOW);

        heartbeat.pulse();

        assertThat(heartbeat.configured()).isTrue();
        assertThat(heartbeat.lastOk()).isEqualTo(NOW);
    }

    @Test
    void failedPingKeepsThePreviousSuccess() {
        MutableClock clock = new MutableClock(NOW);
        Heartbeat heartbeat = heartbeat(clock);

        heartbeat.pulse();
        clock.advance(Duration.ofSeconds(90));
        status.set(500);
        heartbeat.pulse();

        assertThat(heartbeat.lastOk()).isEqualTo(NOW);
    }

    private Heartbeat heartbeat(Instant start) {
        return heartbeat(new MutableClock(start));
    }

    private Heartbeat heartbeat(MutableClock clock) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                exchange.sendResponseHeaders(status.get(), -1);
                exchange.close();
            });
            server.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        RestClient rest = RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build();
        return new Heartbeat(rest, URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/ping"), clock);
    }
}
