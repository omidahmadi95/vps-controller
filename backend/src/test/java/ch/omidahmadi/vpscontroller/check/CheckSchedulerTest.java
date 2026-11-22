package ch.omidahmadi.vpscontroller.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CheckSchedulerTest {

    private final Instant now = Instant.parse("2026-11-23T10:00:00Z");

    @Test
    void neverCheckedIsDue() {
        assertThat(CheckScheduler.due(null, Duration.ofSeconds(60), now)).isTrue();
    }

    @Test
    void dueWhenIntervalHasElapsed() {
        assertThat(CheckScheduler.due(now.minusSeconds(60), Duration.ofSeconds(60), now)).isTrue();
        assertThat(CheckScheduler.due(now.minusSeconds(61), Duration.ofSeconds(60), now)).isTrue();
    }

    @Test
    void notDueBeforeTheIntervalElapsed() {
        assertThat(CheckScheduler.due(now.minusSeconds(30), Duration.ofSeconds(60), now)).isFalse();
    }
}
