package ch.omidahmadi.vpscontroller.overview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ch.omidahmadi.vpscontroller.check.CheckRepository;
import ch.omidahmadi.vpscontroller.check.CheckRepository.OverviewCheck;
import ch.omidahmadi.vpscontroller.check.CheckState;
import ch.omidahmadi.vpscontroller.check.Heartbeat;
import ch.omidahmadi.vpscontroller.container.ContainerService;
import ch.omidahmadi.vpscontroller.container.ContainerState;
import ch.omidahmadi.vpscontroller.container.ContainerView;
import ch.omidahmadi.vpscontroller.container.SourceUnreachableException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OverviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-28T07:12:04Z");

    private final CheckRepository repository = org.mockito.Mockito.mock(CheckRepository.class);
    private final ContainerService containers = org.mockito.Mockito.mock(ContainerService.class);
    private final Heartbeat heartbeat = org.mockito.Mockito.mock(Heartbeat.class);

    private OverviewService service;

    @BeforeEach
    void setUp() {
        service = new OverviewService(repository, containers, heartbeat, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void downCheckIsAProblemIncident() {
        given(repository.checksForOverview()).willReturn(List.of(
                check(7, "api.example.org", CheckState.DOWN, NOW.minusSeconds(200), NOW.minusSeconds(30),
                        "HTTP 503 (erwartet 200-399)", 60, 2, true)));

        Overview o = service.overview();

        assertThat(o.status()).isEqualTo(OverviewStatus.PROBLEM);
        assertThat(o.counts().problem()).isEqualTo(1);
        Overview.Incident i = o.incidents().getFirst();
        assertThat(i.kind()).isEqualTo(Overview.Incident.Kind.CHECK);
        assertThat(i.severity()).isEqualTo(Overview.Incident.Severity.PROBLEM);
        assertThat(i.name()).isEqualTo("api.example.org");
        assertThat(i.state()).isEqualTo("DOWN");
        assertThat(i.label()).isEqualTo("Ausgefallen");
        assertThat(i.detail()).isEqualTo("HTTP 503 (erwartet 200-399)");
        assertThat(i.since()).isEqualTo(NOW.minusSeconds(200));
        assertThat(i.checkId()).isEqualTo(7L);
    }

    @Test
    void pendingCheckIsUncertainAndNeverOk() {
        given(repository.checksForOverview()).willReturn(List.of(
                check(1, "shop.example.org", CheckState.PENDING, null, NOW.minusSeconds(10), null, 60, 2, true)));

        Overview o = service.overview();

        assertThat(o.status()).isEqualTo(OverviewStatus.UNCERTAIN);
        assertThat(o.counts().uncertain()).isEqualTo(1);
        assertThat(o.counts().ok()).isZero();
        assertThat(o.incidents().getFirst().state()).isEqualTo("PENDING");
        assertThat(o.incidents().getFirst().label()).isEqualTo("Wird geprüft");
    }

    @Test
    void upCountsAsOkPausedOnlyCounted() {
        given(repository.checksForOverview()).willReturn(List.of(
                check(1, "shop.example.org", CheckState.UP, NOW.minusSeconds(300), NOW.minusSeconds(20), null, 60, 2, true),
                check(2, "old.example.org", CheckState.PAUSED, NOW.minusSeconds(600), NOW.minusSeconds(600), null, 60, 2, false)));

        Overview o = service.overview();

        assertThat(o.status()).isEqualTo(OverviewStatus.CALM);
        assertThat(o.counts().ok()).isEqualTo(1);
        assertThat(o.counts().paused()).isEqualTo(1);
        assertThat(o.incidents()).isEmpty();
    }

    @Test
    void degradedContainerIsProblemUnreachableIsUncertainRunningAndStoppedCounted() {
        given(containers.containers()).willReturn(List.of(
                container("shop", ContainerState.DEGRADED, "Health-Check schlägt fehl"),
                container("api-1", ContainerState.UNREACHABLE, "Nicht erreichbar"),
                container("wiki-1", ContainerState.UNKNOWN, "Unbekannter Docker-Zustand: created"),
                container("db", ContainerState.RUNNING, "Health-Check ok"),
                container("cache", ContainerState.STOPPED, "Sauber beendet (Exit 0)")));

        Overview o = service.overview();

        assertThat(o.status()).isEqualTo(OverviewStatus.PROBLEM);
        assertThat(o.counts().problem()).isEqualTo(1);
        assertThat(o.counts().uncertain()).isEqualTo(2);
        assertThat(o.counts().ok()).isEqualTo(1);
        assertThat(o.counts().stopped()).isEqualTo(1);
        assertThat(o.incidents()).extracting(Overview.Incident::name).containsExactly("shop", "api-1", "wiki-1");
        assertThat(o.incidents().getFirst().kind()).isEqualTo(Overview.Incident.Kind.CONTAINER);
        assertThat(o.incidents().getFirst().detail()).isEqualTo("Health-Check schlägt fehl");
        assertThat(o.incidents().getFirst().checkId()).isNull();
    }

    @Test
    void unreachableDockerSourceDegradesInsteadOfFailing() {
        given(containers.containers()).willThrow(new SourceUnreachableException("Docker-Quelle nicht erreichbar", null));
        given(repository.checksForOverview()).willReturn(List.of(
                check(1, "shop.example.org", CheckState.UP, null, NOW.minusSeconds(10), null, 60, 2, true)));

        Overview o = service.overview();

        Overview.Source source = source(o, "containers");
        assertThat(source.state()).isEqualTo(SourceState.UNREACHABLE);
        assertThat(source.label()).isEqualTo("Nicht erreichbar");
        assertThat(source.lastSeenAt()).isNull();
        assertThat(o.status()).isEqualTo(OverviewStatus.UNCERTAIN);
        assertThat(o.incidents()).anySatisfy(i -> {
            assertThat(i.kind()).isEqualTo(Overview.Incident.Kind.SOURCE);
            assertThat(i.severity()).isEqualTo(Overview.Incident.Severity.UNCERTAIN);
            assertThat(i.name()).isEqualTo("Container");
            assertThat(i.state()).isEqualTo("UNREACHABLE");
        });
        // No container rows counted: the only ok is the one check.
        assertThat(o.counts().ok()).isEqualTo(1);
        assertThat(o.counts().stopped()).isZero();
    }

    @Test
    void staleCheckTurnsChecksSourceStaleAndAddsAUncertainIncident() {
        given(repository.checksForOverview()).willReturn(List.of(
                check(1, "shop.example.org", CheckState.UP, null, NOW.minusSeconds(400), null, 60, 2, true),
                check(2, "api.example.org", CheckState.UP, null, NOW.minusSeconds(30), null, 120, 2, true)));

        Overview o = service.overview();

        Overview.Source source = source(o, "checks");
        assertThat(source.state()).isEqualTo(SourceState.STALE);
        assertThat(source.label()).isEqualTo("Veraltet");
        assertThat(source.lastSeenAt()).isEqualTo(NOW.minusSeconds(30));
        assertThat(o.incidents()).anySatisfy(i -> {
            assertThat(i.kind()).isEqualTo(Overview.Incident.Kind.SOURCE);
            assertThat(i.name()).isEqualTo("Dienstprüfung");
        });
    }

    @Test
    void checkWithinThreeIntervalsIsNotStale() {
        // 400 s old with a 200 s interval is exactly 2 intervals: still current.
        given(repository.checksForOverview()).willReturn(List.of(
                check(1, "shop.example.org", CheckState.UP, null, NOW.minusSeconds(400), null, 200, 2, true)));

        assertThat(source(service.overview(), "checks").state()).isEqualTo(SourceState.OK);
    }

    @Test
    void noEnabledChecksIsNotConfigured() {
        given(repository.checksForOverview()).willReturn(List.of(
                check(1, "old.example.org", CheckState.PAUSED, null, NOW.minusSeconds(600), null, 60, 2, false)));

        Overview o = service.overview();

        assertThat(source(o, "checks").state()).isEqualTo(SourceState.NOT_CONFIGURED);
        assertThat(source(o, "checks").label()).isEqualTo("Nicht eingerichtet");
        assertThat(o.incidents()).isEmpty();
        assertThat(o.status()).isEqualTo(OverviewStatus.CALM);
    }

    @Test
    void heartbeatWithoutUrlIsNotConfigured() {
        given(heartbeat.configured()).willReturn(false);

        Overview.Source source = source(service.overview(), "heartbeat");

        assertThat(source.state()).isEqualTo(SourceState.NOT_CONFIGURED);
        assertThat(source.lastSeenAt()).isNull();
    }

    @Test
    void heartbeatConfiguredButNeverSucceededIsStale() {
        given(heartbeat.configured()).willReturn(true);
        given(heartbeat.lastOk()).willReturn(null);

        Overview.Source source = source(service.overview(), "heartbeat");

        assertThat(source.state()).isEqualTo(SourceState.STALE);
        assertThat(source.label()).isEqualTo("Veraltet");
        assertThat(source.lastSeenAt()).isNull();
    }

    @Test
    void heartbeatRecentSuccessIsOkOldSuccessIsStale() {
        given(heartbeat.configured()).willReturn(true);
        given(heartbeat.lastOk()).willReturn(NOW.minusSeconds(120));
        assertThat(source(service.overview(), "heartbeat").state()).isEqualTo(SourceState.OK);

        given(heartbeat.lastOk()).willReturn(NOW.minusSeconds(301));
        Overview.Source stale = source(service.overview(), "heartbeat");
        assertThat(stale.state()).isEqualTo(SourceState.STALE);
        assertThat(stale.lastSeenAt()).isEqualTo(NOW.minusSeconds(301));
    }

    @Test
    void incidentsAreOrderedProblemFirstThenOldestThenNameThenNullsLast() {
        given(repository.checksForOverview()).willReturn(List.of(
                check(1, "zebra.example.org", CheckState.DOWN, NOW.minusSeconds(100), NOW.minusSeconds(10), null, 60, 2, true),
                check(2, "alpha.example.org", CheckState.DOWN, NOW.minusSeconds(300), NOW.minusSeconds(10), null, 60, 2, true),
                check(3, "beta.example.org", CheckState.PENDING, NOW.minusSeconds(50), NOW.minusSeconds(10), null, 60, 2, true)));
        given(containers.containers()).willReturn(List.of(
                container("api-1", ContainerState.DEGRADED, "Health-Check schlägt fehl")));

        Overview o = service.overview();

        assertThat(o.status()).isEqualTo(OverviewStatus.PROBLEM);
        // PROBLEM first, oldest since first; the container incident has no 'since' and sorts last within PROBLEM.
        assertThat(o.incidents()).extracting(Overview.Incident::name)
                .containsExactly("alpha.example.org", "zebra.example.org", "api-1", "beta.example.org");
    }

    private static CheckRepository.OverviewCheck check(long id, String name, CheckState state, Instant since,
                                                       Instant lastCheckedAt, String lastError, int intervalSeconds,
                                                       int failureThreshold, boolean enabled) {
        return new OverviewCheck(id, name, state, since, lastCheckedAt, lastError, intervalSeconds, failureThreshold,
                enabled);
    }

    private static ContainerView container(String name, ContainerState state, String detail) {
        return new ContainerView(name, "img:1", state, state.label(), detail);
    }

    private static Overview.Source source(Overview o, String name) {
        return o.sources().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }
}
