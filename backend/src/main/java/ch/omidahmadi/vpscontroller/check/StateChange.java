package ch.omidahmadi.vpscontroller.check;

import java.time.Instant;

/** Result of applying one probe result: the new snapshot plus the transition it caused (if any). */
public record StateChange(CheckState state, int consecutiveFailures, int consecutiveSuccesses, Instant since,
                          Transition transition) {

    StateSnapshot snapshot() {
        return new StateSnapshot(state, consecutiveFailures, consecutiveSuccesses, since);
    }
}
