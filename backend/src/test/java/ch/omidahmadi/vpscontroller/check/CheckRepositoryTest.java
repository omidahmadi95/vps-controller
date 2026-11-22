package ch.omidahmadi.vpscontroller.check;

import static org.assertj.core.api.Assertions.assertThat;

import ch.omidahmadi.vpscontroller.TestPostgres;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = "vps.scheduler.enabled=false")
@Import(TestPostgres.class)
class CheckRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    @Autowired
    CheckRepository repository;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("delete from check_result").update();
        jdbc.sql("delete from check_state").update();
        jdbc.sql("delete from http_check").update();
    }

    @Test
    void syncCreatesChecksWithPendingStateAndKeepsHistoryOnResync() {
        repository.sync(List.of(spec("Shop", "https://shop.example.org/health")), NOW);
        long id = idOf("Shop");
        repository.insertResult(id, NOW.minusSeconds(60), new ProbeResult(true, 200, 80L, null));

        repository.sync(List.of(spec("Shop", "https://shop.example.org/health2")), NOW);

        assertThat(repository.overview(NOW)).hasSize(1);
        CheckView view = repository.overview(NOW).getFirst();
        assertThat(view.url()).isEqualTo("https://shop.example.org/health2");
        assertThat(view.state()).isEqualTo(CheckState.PENDING);
        assertThat(view.lastCheckedAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void checksMissingFromTheFileBecomePausedAndKeepTheirHistory() {
        repository.sync(List.of(spec("Shop", "https://shop.example.org/health"),
                spec("Api", "https://api.example.org/ping")), NOW);
        long shopId = idOf("Shop");
        repository.insertResult(shopId, NOW.minusSeconds(120), new ProbeResult(true, 200, 40L, null));

        repository.sync(List.of(spec("Api", "https://api.example.org/ping")), NOW);

        CheckView shop = byName("Shop");
        assertThat(shop.state()).isEqualTo(CheckState.PAUSED);
        assertThat(shop.label()).isEqualTo("Pausiert");
        assertThat(shop.since()).isEqualTo(NOW);
        assertThat(shop.lastCheckedAt()).isEqualTo(NOW.minusSeconds(120));
    }

    @Test
    void overviewComputesUptimeAndTakesTheNewestResult() {
        repository.sync(List.of(spec("Shop", "https://shop.example.org/health")), NOW);
        long id = idOf("Shop");
        repository.insertResult(id, NOW.minusSeconds(120), new ProbeResult(true, 200, 80L, null));
        repository.insertResult(id, NOW.minusSeconds(60), new ProbeResult(false, 525, null,
                "HTTP 525 (erwartet 200-399)"));

        CheckView view = byName("Shop");

        assertThat(view.lastCheckedAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(view.lastStatusCode()).isEqualTo(525);
        assertThat(view.lastLatencyMs()).isNull();
        assertThat(view.lastError()).isEqualTo("HTTP 525 (erwartet 200-399)");
        assertThat(view.uptime24h()).isEqualTo(50.0);
        assertThat(view.uptime30d()).isEqualTo(50.0);
    }

    @Test
    void uptimeIsNullWithoutResultsInTheWindow() {
        repository.sync(List.of(spec("Shop", "https://shop.example.org/health")), NOW);

        assertThat(byName("Shop").uptime24h()).isNull();
    }

    @Test
    void bucketsGroupByThirtyMinuteWindows() {
        repository.sync(List.of(spec("Shop", "https://shop.example.org/health")), NOW);
        long id = idOf("Shop");
        repository.insertResult(id, NOW.minusSeconds(600), new ProbeResult(true, 200, 100L, null));
        repository.insertResult(id, NOW.plusSeconds(300), new ProbeResult(true, 200, 210L, null));
        repository.insertResult(id, NOW.plusSeconds(420), new ProbeResult(false, 500, null, "HTTP 500"));

        Map<Long, CheckRepository.Bucket> byStart = repository.buckets(id, NOW.minusSeconds(3600)).stream()
                .collect(Collectors.toMap(CheckRepository.Bucket::startEpochSeconds, Function.identity()));

        long nineThirty = Instant.parse("2026-11-23T09:30:00Z").getEpochSecond();
        long ten = Instant.parse("2026-11-23T10:00:00Z").getEpochSecond();

        assertThat(byStart.get(nineThirty).total()).isEqualTo(1);
        assertThat(byStart.get(nineThirty).failed()).isZero();
        assertThat(byStart.get(nineThirty).avgLatencyMs()).isEqualTo(100.0);

        assertThat(byStart.get(ten).total()).isEqualTo(2);
        assertThat(byStart.get(ten).failed()).isEqualTo(1);
        assertThat(byStart.get(ten).avgLatencyMs()).isEqualTo(210.0);
    }

    @Test
    void retentionDeletesOnlyResultsOlderThanTheCutoff() {
        repository.sync(List.of(spec("Shop", "https://shop.example.org/health")), NOW);
        long id = idOf("Shop");
        repository.insertResult(id, NOW.minus(Duration.ofDays(31)), new ProbeResult(true, 200, 10L, null));
        repository.insertResult(id, NOW.minus(Duration.ofDays(1)), new ProbeResult(true, 200, 10L, null));

        int deleted = repository.deleteResultsOlderThan(NOW.minus(Duration.ofDays(30)));

        assertThat(deleted).isEqualTo(1);
        assertThat(countResults(id)).isEqualTo(1);
    }

    @Test
    void existsReflectsConfiguredChecks() {
        repository.sync(List.of(spec("Shop", "https://shop.example.org/health")), NOW);
        long id = idOf("Shop");

        assertThat(repository.exists(id)).isTrue();
        assertThat(repository.exists(id + 999)).isFalse();
    }

    private CheckView byName(String name) {
        return repository.overview(NOW).stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
    }

    private long idOf(String name) {
        return jdbc.sql("select id from http_check where name = :name").param("name", name).query(Long.class).single();
    }

    private int countResults(long id) {
        return jdbc.sql("select count(*) from check_result where check_id = :id").param("id", id)
                .query(Integer.class).single();
    }

    private static CheckSpec spec(String name, String url) {
        return new CheckSpec(name, url, Duration.ofSeconds(60), Duration.ofSeconds(10),
                StatusRanges.parse(List.of("200-399")), 2, 1);
    }
}
