package ch.omidahmadi.vpscontroller.check;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Syncs the checks YAML into the database once the context is up (Flyway has run by then). */
@Component
class CheckSync {

    private static final Logger log = LoggerFactory.getLogger(CheckSync.class);

    private final ChecksFileLoader loader;
    private final CheckRepository repository;
    private final Clock clock;

    CheckSync(ChecksFileLoader loader, CheckRepository repository, Clock clock) {
        this.loader = loader;
        this.repository = repository;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    void sync() {
        List<CheckSpec> specs = loader.load();
        repository.sync(specs, clock.instant());
        log.info("Checks synchronisiert: {} aktiv", specs.size());
    }
}
