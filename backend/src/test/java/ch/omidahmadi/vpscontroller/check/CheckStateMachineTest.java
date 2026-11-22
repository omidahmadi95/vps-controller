package ch.omidahmadi.vpscontroller.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CheckStateMachineTest {

    private final CheckStateMachine machine = new CheckStateMachine();
    private final Instant now = Instant.parse("2026-11-23T10:00:00Z");

    @ParameterizedTest(name = "{0} f={1} s={2} ok={3} -> {6}/{7}")
    @CsvSource(delimiter = '|', textBlock = """
            PENDING | 0 | 0 | true  | 2 | 1 | UP      | NONE      | 0 | 0
            PENDING | 0 | 0 | false | 2 | 1 | PENDING | NONE      | 1 | 0
            PENDING | 1 | 0 | false | 2 | 1 | DOWN    | WENT_DOWN | 2 | 0
            PENDING | 0 | 0 | false | 1 | 1 | DOWN    | WENT_DOWN | 1 | 0
            PENDING | 0 | 0 | true  | 3 | 3 | UP      | NONE      | 0 | 0
            UP      | 0 | 0 | true  | 2 | 1 | UP      | NONE      | 0 | 0
            UP      | 0 | 0 | false | 2 | 1 | UP      | NONE      | 1 | 0
            UP      | 1 | 0 | false | 2 | 1 | DOWN    | WENT_DOWN | 2 | 0
            UP      | 0 | 0 | false | 1 | 1 | DOWN    | WENT_DOWN | 1 | 0
            DOWN    | 2 | 0 | false | 2 | 1 | DOWN    | NONE      | 3 | 0
            DOWN    | 2 | 0 | true  | 2 | 1 | UP      | RECOVERED | 0 | 1
            DOWN    | 2 | 0 | true  | 2 | 2 | DOWN    | NONE      | 0 | 1
            DOWN    | 2 | 1 | true  | 2 | 2 | UP      | RECOVERED | 0 | 2
            """)
    void transitions(CheckState state, int failures, int successes, boolean ok, int failureThreshold,
                     int recoveryThreshold, CheckState expectedState, Transition expectedTransition,
                     int expectedFailures, int expectedSuccesses) {
        StateChange change = machine.apply(new StateSnapshot(state, failures, successes, now.minusSeconds(60)),
                ok, failureThreshold, recoveryThreshold, now);

        assertThat(change.state()).isEqualTo(expectedState);
        assertThat(change.transition()).isEqualTo(expectedTransition);
        assertThat(change.consecutiveFailures()).isEqualTo(expectedFailures);
        assertThat(change.consecutiveSuccesses()).isEqualTo(expectedSuccesses);
    }

    @Test
    void sinceIsKeptWhileStateStaysAndMovesOnTransition() {
        Instant old = Instant.parse("2026-11-23T09:00:00Z");
        // PENDING below threshold keeps the (null) since.
        StateChange stillPending = machine.apply(new StateSnapshot(CheckState.PENDING, 0, 0, null), false, 2, 1, now);
        assertThat(stillPending.since()).isNull();
        // First success sets since to now.
        assertThat(machine.apply(new StateSnapshot(CheckState.PENDING, 0, 0, null), true, 2, 1, now).since())
                .isEqualTo(now);
        // UP stays UP: since unchanged.
        assertThat(machine.apply(new StateSnapshot(CheckState.UP, 0, 0, old), true, 2, 1, now).since())
                .isEqualTo(old);
        // UP -> DOWN: since moves to now.
        assertThat(machine.apply(new StateSnapshot(CheckState.UP, 1, 0, old), false, 2, 1, now).since())
                .isEqualTo(now);
    }

    @Test
    void pausedInputIsTreatedAsNotYetUp() {
        StateChange change = machine.apply(new StateSnapshot(CheckState.PAUSED, 0, 0, null), true, 2, 1, now);
        assertThat(change.state()).isEqualTo(CheckState.UP);
    }
}
