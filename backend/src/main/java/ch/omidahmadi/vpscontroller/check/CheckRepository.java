package ch.omidahmadi.vpscontroller.check;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** All SQL for checks, states and results. Uptime and history buckets are computed in SQL, not in Java. */
@Repository
public class CheckRepository {

    private static final RowMapper<Bucket> BUCKET = (rs, n) -> new Bucket(
            rs.getLong("bucket"), rs.getInt("total"), rs.getInt("failed"), nullableDouble(rs, "avg_latency"));

    private final JdbcClient jdbc;

    public CheckRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** One half-hour history bucket. {@code avgLatencyMs} is null when no result in it had a latency. */
    public record Bucket(long startEpochSeconds, int total, int failed, Double avgLatencyMs) {
    }

    // --- definitions -------------------------------------------------------

    @Transactional
    public void sync(List<CheckSpec> specs, Instant now) {
        List<String> names = specs.stream().map(CheckSpec::name).toList();
        for (CheckSpec spec : specs) {
            long id = upsertDefinition(spec);
            ensureState(id);
        }
        List<String> enabled = jdbc.sql("select name from http_check where enabled").query(String.class).list();
        for (String name : enabled) {
            if (!names.contains(name)) {
                disable(name, now);
            }
        }
    }

    private long upsertDefinition(CheckSpec spec) {
        return jdbc.sql("""
                        insert into http_check (name, url, interval_seconds, timeout_seconds, accept,
                                                failure_threshold, recovery_threshold, enabled)
                        values (:name, :url, :interval, :timeout, :accept, :failure, :recovery, true)
                        on conflict (name) do update set
                            url = excluded.url, interval_seconds = excluded.interval_seconds,
                            timeout_seconds = excluded.timeout_seconds, accept = excluded.accept,
                            failure_threshold = excluded.failure_threshold,
                            recovery_threshold = excluded.recovery_threshold, enabled = true
                        returning id
                        """)
                .param("name", spec.name())
                .param("url", spec.url())
                .param("interval", (int) spec.interval().toSeconds())
                .param("timeout", (int) spec.timeout().toSeconds())
                .param("accept", spec.accept().describe())
                .param("failure", spec.failureThreshold())
                .param("recovery", spec.recoveryThreshold())
                .query(Long.class).single();
    }

    private void ensureState(long id) {
        jdbc.sql("""
                        insert into check_state (check_id, state, consecutive_failures, consecutive_successes, since)
                        values (:id, 'PENDING', 0, 0, null)
                        on conflict (check_id) do nothing
                        """)
                .param("id", id).update();
        // A check that comes back into the file starts fresh, not from its old DOWN/UP state.
        jdbc.sql("""
                        update check_state set state = 'PENDING', consecutive_failures = 0,
                            consecutive_successes = 0, since = null
                        where check_id = :id and state = 'PAUSED'
                        """)
                .param("id", id).update();
    }

    private void disable(String name, Instant now) {
        jdbc.sql("update http_check set enabled = false where name = :name").param("name", name).update();
        jdbc.sql("""
                        update check_state set state = 'PAUSED', consecutive_failures = 0,
                            consecutive_successes = 0, since = :since
                        where check_id = (select id from http_check where name = :name)
                        """)
                .param("name", name).param("since", timestamp(now)).update();
    }

    public List<CheckRuntime> findEnabled() {
        return jdbc.sql("""
                        select c.id, c.name, c.url, c.interval_seconds, c.timeout_seconds, c.accept,
                               c.failure_threshold, c.recovery_threshold, c.enabled,
                               s.state, s.consecutive_failures, s.consecutive_successes, s.since,
                               r.checked_at as last_checked_at
                        from http_check c
                        join check_state s on s.check_id = c.id
                        left join lateral (
                            select checked_at from check_result where check_id = c.id
                            order by checked_at desc, id desc limit 1
                        ) r on true
                        where c.enabled
                        """).query((rs, n) -> new CheckRuntime(definition(rs), snapshot(rs),
                nullableInstant(rs, "last_checked_at"))).list();
    }

    public boolean exists(long id) {
        return jdbc.sql("select exists(select 1 from http_check where id = :id)")
                .param("id", id).query(Boolean.class).single();
    }

    public Optional<StateSnapshot> loadState(long id) {
        return jdbc.sql("""
                        select state, consecutive_failures, consecutive_successes, since
                        from check_state where check_id = :id
                        """).param("id", id).query((rs, n) -> snapshot(rs)).optional();
    }

    public void saveState(long id, StateChange change) {
        jdbc.sql("""
                        update check_state set state = :state, consecutive_failures = :failures,
                            consecutive_successes = :successes, since = :since
                        where check_id = :id
                        """)
                .param("state", change.state().name())
                .param("failures", change.consecutiveFailures())
                .param("successes", change.consecutiveSuccesses())
                .param("since", timestamp(change.since()))
                .param("id", id)
                .update();
    }

    public void insertResult(long id, Instant checkedAt, ProbeResult result) {
        jdbc.sql("""
                        insert into check_result (check_id, checked_at, ok, status_code, latency_ms, error)
                        values (:id, :checkedAt, :ok, :code, :latency, :error)
                        """)
                .param("id", id)
                .param("checkedAt", timestamp(checkedAt))
                .param("ok", result.ok())
                .param("code", result.statusCode())
                .param("latency", result.latencyMs() == null ? null : result.latencyMs().intValue())
                .param("error", result.error())
                .update();
    }

    public int deleteResultsOlderThan(Instant cutoff) {
        return jdbc.sql("delete from check_result where checked_at < :cutoff")
                .param("cutoff", timestamp(cutoff)).update();
    }

    // --- read model --------------------------------------------------------

    public List<CheckView> overview(Instant now) {
        return jdbc.sql("""
                        select c.id, c.name, c.url, c.enabled, s.state, s.since,
                               r.checked_at as last_checked_at, r.status_code as last_status_code,
                               r.latency_ms as last_latency_ms, r.error as last_error,
                               u24.pct as uptime24h, u30.pct as uptime30d
                        from http_check c
                        left join check_state s on s.check_id = c.id
                        left join lateral (
                            select checked_at, status_code, latency_ms, error from check_result
                            where check_id = c.id order by checked_at desc, id desc limit 1
                        ) r on true
                        left join lateral (
                            select round(100.0 * count(*) filter (where ok) / nullif(count(*), 0), 1)::float8 as pct
                            from check_result where check_id = c.id and checked_at >= :from24
                        ) u24 on true
                        left join lateral (
                            select round(100.0 * count(*) filter (where ok) / nullif(count(*), 0), 1)::float8 as pct
                            from check_result where check_id = c.id and checked_at >= :from30
                        ) u30 on true
                        """)
                .param("from24", timestamp(now.minusSeconds(24 * 3600)))
                .param("from30", timestamp(now.minusSeconds(30L * 24 * 3600)))
                .query((rs, n) -> view(rs))
                .list();
    }

    public List<Bucket> buckets(long checkId, Instant from) {
        return jdbc.sql("""
                        select (extract(epoch from checked_at)::bigint / 1800) * 1800 as bucket,
                               count(*) as total,
                               count(*) filter (where not ok) as failed,
                               avg(latency_ms)::float8 as avg_latency
                        from check_result
                        where check_id = :id and checked_at >= :from
                        group by 1
                        """)
                .param("id", checkId).param("from", timestamp(from)).query(BUCKET).list();
    }

    // --- mapping -----------------------------------------------------------

    private static CheckDefinition definition(ResultSet rs) throws SQLException {
        return new CheckDefinition(rs.getLong("id"), rs.getString("name"), rs.getString("url"),
                java.time.Duration.ofSeconds(rs.getInt("interval_seconds")),
                java.time.Duration.ofSeconds(rs.getInt("timeout_seconds")),
                StatusRanges.parseCsv(rs.getString("accept")),
                rs.getInt("failure_threshold"), rs.getInt("recovery_threshold"), rs.getBoolean("enabled"));
    }

    private static StateSnapshot snapshot(ResultSet rs) throws SQLException {
        return new StateSnapshot(CheckState.valueOf(rs.getString("state")), rs.getInt("consecutive_failures"),
                rs.getInt("consecutive_successes"), nullableInstant(rs, "since"));
    }

    private static CheckView view(ResultSet rs) throws SQLException {
        boolean enabled = rs.getBoolean("enabled");
        CheckState state = enabled ? CheckState.valueOf(rs.getString("state")) : CheckState.PAUSED;
        return CheckView.of(rs.getLong("id"), rs.getString("name"), rs.getString("url"), state,
                nullableInstant(rs, "since"), nullableInstant(rs, "last_checked_at"),
                nullableInt(rs, "last_status_code"), nullableLong(rs, "last_latency_ms"),
                rs.getString("last_error"), nullableDouble(rs, "uptime24h"), nullableDouble(rs, "uptime30d"));
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }
}
