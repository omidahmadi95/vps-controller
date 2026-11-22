package ch.omidahmadi.vpscontroller.check;

import java.time.Instant;

/**
 * Pure, side-effect-free availability state machine.
 *
 * <ul>
 *   <li>PENDING → UP on the first success; failures count, → DOWN after {@code failureThreshold}.</li>
 *   <li>UP stays UP until {@code failureThreshold} consecutive failures.</li>
 *   <li>DOWN → UP after {@code recoveryThreshold} consecutive successes.</li>
 * </ul>
 *
 * {@code since} is the moment the current state began; it changes only on a transition.
 */
public final class CheckStateMachine {

    public StateChange apply(StateSnapshot current, boolean ok, int failureThreshold, int recoveryThreshold,
                             Instant now) {
        return switch (current.state()) {
            case PENDING -> pending(current, ok, failureThreshold, now);
            case UP -> up(current, ok, failureThreshold, now);
            case DOWN -> down(current, ok, recoveryThreshold, now);
            // PAUSED is a configuration state, not a probe outcome; treat it as not-yet-up.
            default -> pending(new StateSnapshot(CheckState.PENDING, current.consecutiveFailures(),
                    current.consecutiveSuccesses(), current.since()), ok, failureThreshold, now);
        };
    }

    private StateChange pending(StateSnapshot current, boolean ok, int failureThreshold, Instant now) {
        if (ok) {
            return new StateChange(CheckState.UP, 0, 0, now, Transition.NONE);
        }
        int failures = current.consecutiveFailures() + 1;
        if (failures >= failureThreshold) {
            return new StateChange(CheckState.DOWN, failures, 0, now, Transition.WENT_DOWN);
        }
        return new StateChange(CheckState.PENDING, failures, 0, current.since(), Transition.NONE);
    }

    private StateChange up(StateSnapshot current, boolean ok, int failureThreshold, Instant now) {
        if (ok) {
            return new StateChange(CheckState.UP, 0, 0, current.since(), Transition.NONE);
        }
        int failures = current.consecutiveFailures() + 1;
        if (failures >= failureThreshold) {
            return new StateChange(CheckState.DOWN, failures, 0, now, Transition.WENT_DOWN);
        }
        return new StateChange(CheckState.UP, failures, 0, current.since(), Transition.NONE);
    }

    private StateChange down(StateSnapshot current, boolean ok, int recoveryThreshold, Instant now) {
        if (!ok) {
            return new StateChange(CheckState.DOWN, current.consecutiveFailures() + 1, 0, current.since(),
                    Transition.NONE);
        }
        int successes = current.consecutiveSuccesses() + 1;
        if (successes >= recoveryThreshold) {
            return new StateChange(CheckState.UP, 0, successes, now, Transition.RECOVERED);
        }
        return new StateChange(CheckState.DOWN, 0, successes, current.since(), Transition.NONE);
    }
}
