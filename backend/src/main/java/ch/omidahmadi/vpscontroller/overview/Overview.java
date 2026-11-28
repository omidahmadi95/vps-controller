package ch.omidahmadi.vpscontroller.overview;

import java.time.Instant;
import java.util.List;

/**
 * The {@code GET /api/overview} answer: what needs the operator right now, where the data came from and what
 * happened in the last seven days. Plain data — every decision is made in {@link OverviewService}.
 */
public record Overview(Instant checkedAt, OverviewStatus status, Counts counts, List<Incident> incidents,
                       List<Source> sources, List<RecentOutage> recentOutages) {

    /** One row per state class. {@code problem}/{@code uncertain} equal the incident counts of that severity. */
    public record Counts(int problem, int uncertain, int ok, int stopped, int paused) {
    }

    /** One thing that needs attention. {@code checkId} is null unless {@code kind} is CHECK. */
    public record Incident(Kind kind, Severity severity, String name, String state, String label, Instant since,
                           String detail, Long checkId) {

        public enum Kind {
            CHECK, CONTAINER, SOURCE
        }

        public enum Severity {
            PROBLEM, UNCERTAIN
        }
    }

    /** Freshness of one source: {@code checks}, {@code containers} or {@code heartbeat}. */
    public record Source(String name, SourceState state, String label, Instant lastSeenAt) {
    }

    /** One maximal run of failed probes in the last seven days, at least the check's failure threshold long. */
    public record RecentOutage(long checkId, String name, Instant start, Instant end, long durationSeconds,
                               int failures) {
    }
}
