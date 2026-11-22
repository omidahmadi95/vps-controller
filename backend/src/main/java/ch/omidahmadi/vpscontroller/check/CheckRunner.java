package ch.omidahmadi.vpscontroller.check;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Runs one check end-to-end: probe → state machine → persist → notify. */
@Component
public class CheckRunner {

    private final HttpProbe probe;
    private final CheckRepository repository;
    private final CheckStateMachine machine;
    private final Notifier notifier;
    private final Clock clock;

    public CheckRunner(HttpProbe probe, CheckRepository repository, CheckStateMachine machine, Notifier notifier,
                       Clock clock) {
        this.probe = probe;
        this.repository = repository;
        this.machine = machine;
        this.notifier = notifier;
        this.clock = clock;
    }

    public void run(CheckDefinition definition) {
        Instant now = clock.instant();
        ProbeResult result = probe.check(definition.url(), definition.timeout(), definition.accept());
        repository.insertResult(definition.id(), now, result);

        StateSnapshot current = repository.loadState(definition.id()).orElseGet(StateSnapshot::pending);
        StateChange change = machine.apply(current, result.ok(), definition.failureThreshold(),
                definition.recoveryThreshold(), now);
        repository.saveState(definition.id(), change);

        switch (change.transition()) {
            case WENT_DOWN -> notifier.wentDown(definition.name(), result.error(), now);
            case RECOVERED -> notifier.recovered(definition.name(), outage(current.since(), now));
            case NONE -> {
            }
        }
    }

    private static Duration outage(Instant downSince, Instant now) {
        return downSince == null ? Duration.ZERO : Duration.between(downSince, now);
    }
}
