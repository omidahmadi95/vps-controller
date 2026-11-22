package ch.omidahmadi.vpscontroller.check;

import java.time.Duration;
import java.time.Instant;

/** How a state change is announced. Implementations must never let a failure break checking. */
public interface Notifier {

    void wentDown(String name, String error, Instant at);

    void recovered(String name, Duration outage);
}
