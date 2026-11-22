package ch.omidahmadi.vpscontroller.check;

import java.time.Duration;

/** One entry of the checks YAML file, before it is stored. */
public record CheckSpec(String name, String url, Duration interval, Duration timeout, StatusRanges accept,
                        int failureThreshold, int recoveryThreshold) {

    public CheckSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name fehlt");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url fehlt für Check " + name);
        }
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval muss positiv sein für Check " + name);
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout muss positiv sein für Check " + name);
        }
        if (failureThreshold < 1 || recoveryThreshold < 1) {
            throw new IllegalArgumentException("Schwellen müssen >= 1 sein für Check " + name);
        }
    }

    public static CheckSpec withDefaults(String name, String url) {
        return new CheckSpec(name, url, Duration.ofSeconds(60), Duration.ofSeconds(10),
                StatusRanges.parse(java.util.List.of("200-399")), 2, 1);
    }
}
