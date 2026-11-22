package ch.omidahmadi.vpscontroller.check;

import java.time.Instant;

/** One check with its persisted state and last result time, as the scheduler needs it. */
public record CheckRuntime(CheckDefinition definition, StateSnapshot state, Instant lastCheckedAt) {
}
