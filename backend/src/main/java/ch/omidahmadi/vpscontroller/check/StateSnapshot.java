package ch.omidahmadi.vpscontroller.check;

import java.time.Instant;

/** Persisted state of one check (table {@code check_state}). {@code since} is null while PENDING with no result yet. */
public record StateSnapshot(CheckState state, int consecutiveFailures, int consecutiveSuccesses, Instant since) {

    public static StateSnapshot pending() {
        return new StateSnapshot(CheckState.PENDING, 0, 0, null);
    }
}
