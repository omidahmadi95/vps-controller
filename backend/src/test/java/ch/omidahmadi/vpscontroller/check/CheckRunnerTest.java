package ch.omidahmadi.vpscontroller.check;

import static org.assertj.core.api.Assertions.assertThat;

import ch.omidahmadi.vpscontroller.TestPostgres;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The whole pipeline end-to-end: real probe against a local server, real state machine and Postgres, fake clock. */
@SpringBootTest(properties = "vps.scheduler.enabled=false")
@Import(TestPostgres.class)
class CheckRunnerTest {

    private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");

    @Autowired
    CheckRepository repository;

    @Autowired
    JdbcClient jdbc;

    private final MutableClock clock = new MutableClock(T0);
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final AtomicInteger status = new AtomicInteger(200);
    private HttpServer server;
    private CheckRunner runner;

    @BeforeEach
    void setUp() throws IOException {
        jdbc.sql("delete from check_result").update();
        jdbc.sql("delete from check_state").update();
        jdbc.sql("delete from http_check").update();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
        runner = new CheckRunner(new HttpProbe(), repository, new CheckStateMachine(), notifier, clock);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void firstSuccessMovesPendingToUpWithoutAnAlert() {
        CheckDefinition definition = configure(1, 1);

        clock.set(T0);
        runner.run(definition);

        CheckView view = repository.overview(T0).getFirst();
        assertThat(view.state()).isEqualTo(CheckState.UP);
        assertThat(view.since()).isEqualTo(T0);
        assertThat(notifier.down).isEmpty();
        assertThat(notifier.recovered).isEmpty();
    }

    @Test
    void aFailureAlertsWentDownAndARecoveryAlertsAfterTheOutage() {
        CheckDefinition definition = configure(1, 1);

        clock.set(T0);
        status.set(500);
        runner.run(definition);

        assertThat(notifier.down).containsExactly(
                "Ausgefallen: Shop – HTTP 500 (erwartet 200-399) (seit 10:00)");
        assertThat(repository.overview(T0).getFirst().state()).isEqualTo(CheckState.DOWN);

        clock.set(T0.plus(Duration.ofMinutes(12)));
        status.set(200);
        runner.run(definition);

        assertThat(notifier.recovered).containsExactly("Wieder erreichbar: Shop nach 12 min");
        CheckView view = repository.overview(T0.plus(Duration.ofMinutes(12))).getFirst();
        assertThat(view.state()).isEqualTo(CheckState.UP);
        assertThat(view.since()).isEqualTo(T0.plus(Duration.ofMinutes(12)));
        assertThat(view.lastStatusCode()).isEqualTo(200);
    }

    private CheckDefinition configure(int failureThreshold, int recoveryThreshold) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/health";
        repository.sync(List.of(new CheckSpec("Shop", url, Duration.ofSeconds(60), Duration.ofSeconds(2),
                StatusRanges.parse(List.of("200-399")), failureThreshold, recoveryThreshold)), T0);
        return repository.findEnabled().getFirst().definition();
    }

    static class RecordingNotifier implements Notifier {

        final List<String> down = new ArrayList<>();
        final List<String> recovered = new ArrayList<>();

        @Override
        public void wentDown(String name, String error, Instant at) {
            down.add(CheckMessages.wentDown(name, error, at, ZoneOffset.UTC));
        }

        @Override
        public void recovered(String name, Duration outage) {
            recovered.add(CheckMessages.recovered(name, outage));
        }
    }
}
