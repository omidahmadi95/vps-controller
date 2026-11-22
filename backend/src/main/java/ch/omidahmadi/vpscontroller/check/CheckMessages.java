package ch.omidahmadi.vpscontroller.check;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** German alert texts, in one place so Log and Telegram notifiers say exactly the same thing. */
final class CheckMessages {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private CheckMessages() {
    }

    static String wentDown(String name, String error, Instant at, ZoneId zone) {
        return "Ausgefallen: " + name + " – " + error + " (seit " + HH_MM.format(at.atZone(zone)) + ")";
    }

    static String recovered(String name, Duration outage) {
        return "Wieder erreichbar: " + name + " nach " + human(outage);
    }

    static String human(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        if (seconds < 60) {
            return seconds + " s";
        }
        if (seconds < 3600) {
            return (seconds / 60) + " min";
        }
        return (seconds / 3600) + " h " + ((seconds % 3600) / 60) + " min";
    }
}
