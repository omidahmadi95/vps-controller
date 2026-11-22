package ch.omidahmadi.vpscontroller.check;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Dead-man switch: pings an external URL after a successful scheduler cycle, at most once per 60 s.
 * Failures are only logged — the heartbeat must never affect checking.
 */
public class Heartbeat {

    private static final Logger log = LoggerFactory.getLogger(Heartbeat.class);
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(60);

    private final RestClient rest;
    private final URI url;
    private final Clock clock;

    private volatile Instant lastPing;

    public Heartbeat(RestClient rest, URI url, Clock clock) {
        this.rest = rest;
        this.url = url;
        this.clock = clock;
    }

    public void pulse() {
        if (url == null) {
            return;
        }
        Instant now = clock.instant();
        if (lastPing != null && Duration.between(lastPing, now).compareTo(MIN_INTERVAL) < 0) {
            return;
        }
        lastPing = now;
        try {
            rest.get().uri(url).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Heartbeat fehlgeschlagen: {}", e.getClass().getSimpleName());
        }
    }
}
