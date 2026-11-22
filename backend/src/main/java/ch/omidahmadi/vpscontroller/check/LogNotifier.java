package ch.omidahmadi.vpscontroller.check;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fallback when no Telegram credentials are configured: the alerts go to the log. */
class LogNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(LogNotifier.class);

    @Override
    public void wentDown(String name, String error, Instant at) {
        log.warn("{}", CheckMessages.wentDown(name, error, at, java.time.ZoneId.systemDefault()));
    }

    @Override
    public void recovered(String name, Duration outage) {
        log.info("{}", CheckMessages.recovered(name, outage));
    }
}
