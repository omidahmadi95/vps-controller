package ch.omidahmadi.vpscontroller.overview;

import ch.omidahmadi.vpscontroller.check.CheckRepository;
import ch.omidahmadi.vpscontroller.check.Heartbeat;
import ch.omidahmadi.vpscontroller.container.ContainerService;
import ch.omidahmadi.vpscontroller.container.ContainerView;
import ch.omidahmadi.vpscontroller.container.SourceUnreachableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Builds the problem-first overview from the three real sources. A failing source degrades one part of the
 * answer, never the endpoint: the Docker source being unreachable becomes an UNREACHABLE source plus one
 * UNCERTAIN incident, never an empty "all fine".
 */
@Service
public class OverviewService {

    /** A source is stale once a check missed more than three of its own intervals. */
    private static final int STALE_INTERVALS = 3;
    private static final Duration HEARTBEAT_STALE = Duration.ofMinutes(5);
    private static final Duration WINDOW = Duration.ofDays(7);

    private final CheckRepository repository;
    private final ContainerService containers;
    private final Heartbeat heartbeat;
    private final Clock clock;

    public OverviewService(CheckRepository repository, ContainerService containers, Heartbeat heartbeat, Clock clock) {
        this.repository = repository;
        this.containers = containers;
        this.heartbeat = heartbeat;
        this.clock = clock;
    }

    public Overview overview() {
        Instant now = clock.instant();
        List<CheckRepository.OverviewCheck> checks = repository.checksForOverview();

        List<Overview.Incident> incidents = new ArrayList<>();
        int ok = 0;
        int stopped = 0;
        int paused = 0;

        for (CheckRepository.OverviewCheck c : checks) {
            switch (c.state()) {
                case DOWN -> incidents.add(checkIncident(c, Overview.Incident.Severity.PROBLEM));
                case PENDING -> incidents.add(checkIncident(c, Overview.Incident.Severity.UNCERTAIN));
                case UP -> ok++;
                case PAUSED -> paused++;
            }
        }

        Overview.Source containerSource;
        try {
            for (ContainerView v : containers.containers()) {
                switch (v.state()) {
                    case DEGRADED -> incidents.add(containerIncident(v, Overview.Incident.Severity.PROBLEM));
                    case UNREACHABLE, UNKNOWN -> incidents.add(containerIncident(v, Overview.Incident.Severity.UNCERTAIN));
                    case RUNNING -> ok++;
                    case STOPPED -> stopped++;
                }
            }
            containerSource = new Overview.Source("containers", SourceState.OK, SourceState.OK.label(), now);
        } catch (SourceUnreachableException e) {
            containerSource = new Overview.Source("containers", SourceState.UNREACHABLE,
                    SourceState.UNREACHABLE.label(), null);
            incidents.add(new Overview.Incident(Overview.Incident.Kind.SOURCE, Overview.Incident.Severity.UNCERTAIN,
                    "Container", SourceState.UNREACHABLE.name(), SourceState.UNREACHABLE.label(), null, null, null));
        }

        Overview.Source checksSource = checksSource(checks, now, incidents);
        Overview.Source heartbeatSource = heartbeatSource(now);

        incidents.sort(Comparator
                .comparingInt((Overview.Incident i) ->
                        i.severity() == Overview.Incident.Severity.PROBLEM ? 0 : 1)
                .thenComparing(Overview.Incident::since, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Overview.Incident::name));

        int problem = (int) incidents.stream()
                .filter(i -> i.severity() == Overview.Incident.Severity.PROBLEM).count();
        int uncertain = (int) incidents.stream()
                .filter(i -> i.severity() == Overview.Incident.Severity.UNCERTAIN).count();

        OverviewStatus status = problem > 0 ? OverviewStatus.PROBLEM
                : uncertain > 0 ? OverviewStatus.UNCERTAIN : OverviewStatus.CALM;

        return new Overview(now, status, new Overview.Counts(problem, uncertain, ok, stopped, paused), incidents,
                List.of(checksSource, containerSource, heartbeatSource),
                RecentOutages.of(repository.resultsForOverview(now.minus(WINDOW)), now));
    }

    private Overview.Source checksSource(List<CheckRepository.OverviewCheck> checks, Instant now,
                                        List<Overview.Incident> incidents) {
        List<CheckRepository.OverviewCheck> enabled = checks.stream()
                .filter(CheckRepository.OverviewCheck::enabled).toList();
        if (enabled.isEmpty()) {
            return new Overview.Source("checks", SourceState.NOT_CONFIGURED, SourceState.NOT_CONFIGURED.label(), null);
        }
        Instant newest = enabled.stream().map(CheckRepository.OverviewCheck::lastCheckedAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        boolean stale = enabled.stream().anyMatch(c -> c.lastCheckedAt() != null && c.lastCheckedAt()
                .isBefore(now.minus(Duration.ofSeconds((long) STALE_INTERVALS * c.intervalSeconds()))));
        if (stale) {
            incidents.add(new Overview.Incident(Overview.Incident.Kind.SOURCE, Overview.Incident.Severity.UNCERTAIN,
                    "Dienstprüfung", SourceState.STALE.name(), SourceState.STALE.label(), null, null, null));
            return new Overview.Source("checks", SourceState.STALE, SourceState.STALE.label(), newest);
        }
        return new Overview.Source("checks", SourceState.OK, SourceState.OK.label(), newest);
    }

    private Overview.Source heartbeatSource(Instant now) {
        if (!heartbeat.configured()) {
            return new Overview.Source("heartbeat", SourceState.NOT_CONFIGURED, SourceState.NOT_CONFIGURED.label(),
                    null);
        }
        Instant lastOk = heartbeat.lastOk();
        // No incident here: the external service alerts on its own when the heartbeat stops.
        SourceState state = lastOk == null || lastOk.isBefore(now.minus(HEARTBEAT_STALE))
                ? SourceState.STALE : SourceState.OK;
        return new Overview.Source("heartbeat", state, state.label(), lastOk);
    }

    private static Overview.Incident checkIncident(CheckRepository.OverviewCheck c, Overview.Incident.Severity severity) {
        return new Overview.Incident(Overview.Incident.Kind.CHECK, severity, c.name(), c.state().name(),
                c.state().label(), c.since(), c.lastError(), c.id());
    }

    private static Overview.Incident containerIncident(ContainerView v, Overview.Incident.Severity severity) {
        return new Overview.Incident(Overview.Incident.Kind.CONTAINER, severity, v.name(), v.state().name(),
                v.label(), null, v.detail(), null);
    }
}
