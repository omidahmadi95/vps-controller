package ch.omidahmadi.vpscontroller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tiny liveness endpoint for the container healthcheck (no actuator needed). */
@RestController
class HealthController {

    @GetMapping("/api/health")
    Map<String, Object> health() {
        return Map.of("ok", true);
    }
}
