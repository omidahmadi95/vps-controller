package ch.omidahmadi.vpscontroller.overview;

import static org.assertj.core.api.Assertions.assertThat;

import ch.omidahmadi.vpscontroller.check.CheckRepository.OverviewResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecentOutagesTest {

    private static final Instant NOW = Instant.parse("2026-11-28T07:12:04Z");

    @Test
    void runShorterThanThresholdIsNotAnOutage() {
        List<OverviewResult> rows = List.of(
                res(1, "shop.example.org", 2, NOW.minusSeconds(300), false),
                res(1, "shop.example.org", 2, NOW.minusSeconds(240), true));

        assertThat(RecentOutages.of(rows, NOW)).isEmpty();
    }

    @Test
    void runExactlyAtThresholdIsAnOutage() {
        List<OverviewResult> rows = List.of(
                res(1, "shop.example.org", 2, NOW.minusSeconds(300), false),
                res(1, "shop.example.org", 2, NOW.minusSeconds(240), false),
                res(1, "shop.example.org", 2, NOW.minusSeconds(180), true));

        List<Overview.RecentOutage> outages = RecentOutages.of(rows, NOW);

        assertThat(outages).hasSize(1);
        Overview.RecentOutage o = outages.getFirst();
        assertThat(o.checkId()).isEqualTo(1L);
        assertThat(o.name()).isEqualTo("shop.example.org");
        assertThat(o.start()).isEqualTo(NOW.minusSeconds(300));
        assertThat(o.end()).isEqualTo(NOW.minusSeconds(180));
        assertThat(o.durationSeconds()).isEqualTo(120);
        assertThat(o.failures()).isEqualTo(2);
    }

    @Test
    void ongoingRunHasNoEndAndMeasuresUntilNow() {
        List<OverviewResult> rows = List.of(
                res(1, "shop.example.org", 2, NOW.minusSeconds(300), true),
                res(1, "shop.example.org", 2, NOW.minusSeconds(240), false),
                res(1, "shop.example.org", 2, NOW.minusSeconds(180), false));

        Overview.RecentOutage o = RecentOutages.of(rows, NOW).getFirst();

        assertThat(o.start()).isEqualTo(NOW.minusSeconds(240));
        assertThat(o.end()).isNull();
        assertThat(o.durationSeconds()).isEqualTo(240);
        assertThat(o.failures()).isEqualTo(2);
    }

    @Test
    void oneSuccessSplitsTwoRuns() {
        List<OverviewResult> rows = List.of(
                res(1, "shop.example.org", 2, NOW.minusSeconds(500), false),
                res(1, "shop.example.org", 2, NOW.minusSeconds(440), false),
                res(1, "shop.example.org", 2, NOW.minusSeconds(380), true),
                res(1, "shop.example.org", 2, NOW.minusSeconds(320), false),
                res(1, "shop.example.org", 2, NOW.minusSeconds(260), false));

        List<Overview.RecentOutage> outages = RecentOutages.of(rows, NOW);

        assertThat(outages).hasSize(2);
        assertThat(outages.get(0).start()).isEqualTo(NOW.minusSeconds(320));
        assertThat(outages.get(0).end()).isNull();
        assertThat(outages.get(1).start()).isEqualTo(NOW.minusSeconds(500));
        assertThat(outages.get(1).end()).isEqualTo(NOW.minusSeconds(380));
    }

    @Test
    void newestStartFirstAndAtMostTwenty() {
        List<OverviewResult> rows = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            Instant start = NOW.minusSeconds((i + 1) * 100L);
            rows.add(res(i, "check-" + i, 2, start, false));
            rows.add(res(i, "check-" + i, 2, start.plusSeconds(60), false));
        }

        List<Overview.RecentOutage> outages = RecentOutages.of(rows, NOW);

        assertThat(outages).hasSize(20);
        assertThat(outages.getFirst().checkId()).isEqualTo(0L);
        assertThat(outages.get(1).checkId()).isEqualTo(1L);
        assertThat(outages.getFirst().start()).isAfter(outages.get(1).start());
    }

    private static OverviewResult res(long checkId, String name, int threshold, Instant at, boolean ok) {
        return new OverviewResult(checkId, name, threshold, at, ok);
    }
}
