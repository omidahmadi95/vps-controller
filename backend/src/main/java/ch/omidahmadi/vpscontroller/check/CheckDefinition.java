package ch.omidahmadi.vpscontroller.check;

import java.time.Duration;

/** A stored check definition (table {@code http_check}). */
public record CheckDefinition(long id, String name, String url, Duration interval, Duration timeout,
                              StatusRanges accept, int failureThreshold, int recoveryThreshold, boolean enabled) {
}
