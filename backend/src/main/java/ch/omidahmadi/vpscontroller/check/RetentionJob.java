package ch.omidahmadi.vpscontroller.check;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily cleanup: results older than 30 days are deleted so the table stays bounded. */
@Component
@ConditionalOnProperty(name = "vps.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final CheckRepository repository;
    private final Clock clock;

    public RetentionJob(CheckRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *")
    void purge() {
        int deleted = repository.deleteResultsOlderThan(clock.instant().minus(Duration.ofDays(30)));
        log.info("Retention: {} alte Prüfergebnisse gelöscht", deleted);
    }
}
