package ch.omidahmadi.vpscontroller.check;

import java.time.Instant;

/** API row of {@code GET /api/checks}. {@code label} is the German text for {@code state}. */
public record CheckView(long id, String name, String url, CheckState state, String label, Instant since,
                        Instant lastCheckedAt, Integer lastStatusCode, Long lastLatencyMs, String lastError,
                        Double uptime24h, Double uptime30d) {

    static CheckView of(long id, String name, String url, CheckState state, Instant since, Instant lastCheckedAt,
                        Integer lastStatusCode, Long lastLatencyMs, String lastError, Double uptime24h,
                        Double uptime30d) {
        return new CheckView(id, name, url, state, state.label(), since, lastCheckedAt, lastStatusCode, lastLatencyMs,
                lastError, uptime24h, uptime30d);
    }
}
