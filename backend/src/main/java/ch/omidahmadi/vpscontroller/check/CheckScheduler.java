package ch.omidahmadi.vpscontroller.check;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * One tick every 5 s runs all due checks concurrently on virtual threads; each check is at most once in flight.
 * After a tick the heartbeat is pinged.
 */
@Component
@ConditionalOnProperty(name = "vps.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class CheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(CheckScheduler.class);

    private final CheckRepository repository;
    private final CheckRunner runner;
    private final Heartbeat heartbeat;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public CheckScheduler(CheckRepository repository, CheckRunner runner, Heartbeat heartbeat, Clock clock) {
        this.repository = repository;
        this.runner = runner;
        this.heartbeat = heartbeat;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 5000)
    void tick() {
        Instant now = clock.instant();
        for (CheckRuntime runtime : repository.findEnabled()) {
            CheckDefinition definition = runtime.definition();
            if (!due(runtime.lastCheckedAt(), definition.interval(), now) || !inFlight.add(definition.id())) {
                continue;
            }
            executor.execute(() -> {
                try {
                    runner.run(definition);
                } catch (RuntimeException e) {
                    log.warn("Check {} fehlgeschlagen: {}", definition.name(), e.getClass().getSimpleName());
                } finally {
                    inFlight.remove(definition.id());
                }
            });
        }
        heartbeat.pulse();
    }

    static boolean due(Instant lastCheckedAt, Duration interval, Instant now) {
        return lastCheckedAt == null || !lastCheckedAt.plus(interval).isAfter(now);
    }
}
