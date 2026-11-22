package ch.omidahmadi.vpscontroller.container;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Turns Docker's raw state + status text into one unambiguous state.
 * Docker reports health and exit codes only inside the free-text {@code Status}
 * ("Up 3 days (unhealthy)", "Exited (1) 2 hours ago"), so they are parsed here.
 */
@Component
public class ContainerStatusMapper {

    private static final Pattern EXIT_CODE = Pattern.compile("^Exited \\((-?\\d+)\\)");

    public ContainerView map(DockerContainer c) {
        String name = displayName(c.names(), c.id());
        String image = c.image() == null ? "" : c.image();
        String status = c.status() == null ? "" : c.status();
        String state = c.state() == null ? "" : c.state().toLowerCase(Locale.ROOT);

        return switch (state) {
            case "running" -> running(name, image, status);
            case "restarting" -> ContainerView.of(name, image, ContainerState.DEGRADED, "Startet wiederholt neu");
            case "dead" -> ContainerView.of(name, image, ContainerState.DEGRADED, "Container ist tot (dead)");
            case "exited" -> exited(name, image, status);
            case "paused" -> ContainerView.of(name, image, ContainerState.STOPPED, "Pausiert");
            case "created" -> ContainerView.of(name, image, ContainerState.STOPPED, "Erstellt, nie gestartet");
            case "removing" -> ContainerView.of(name, image, ContainerState.UNKNOWN, "Wird entfernt");
            default -> ContainerView.of(name, image, ContainerState.UNKNOWN, "Unbekannter Docker-Zustand: " + (state.isEmpty() ? "leer" : state));
        };
    }

    private ContainerView running(String name, String image, String status) {
        if (status.contains("(unhealthy)")) {
            return ContainerView.of(name, image, ContainerState.DEGRADED, "Health-Check schlägt fehl");
        }
        if (status.contains("(health: starting)")) {
            // Not proven healthy yet: never report it as running.
            return ContainerView.of(name, image, ContainerState.UNKNOWN, "Health-Check läuft noch");
        }
        String detail = status.contains("(healthy)") ? "Health-Check ok" : "Kein Health-Check definiert";
        return ContainerView.of(name, image, ContainerState.RUNNING, detail);
    }

    private ContainerView exited(String name, String image, String status) {
        Matcher m = EXIT_CODE.matcher(status);
        if (!m.find()) {
            return ContainerView.of(name, image, ContainerState.UNKNOWN, "Beendet, Exit-Code unbekannt");
        }
        int code = Integer.parseInt(m.group(1));
        return code == 0
                ? ContainerView.of(name, image, ContainerState.STOPPED, "Sauber beendet (Exit 0)")
                : ContainerView.of(name, image, ContainerState.DEGRADED, "Abgestürzt (Exit " + code + ")");
    }

    static String displayName(List<String> names, String id) {
        if (names != null && !names.isEmpty() && names.getFirst() != null && !names.getFirst().isBlank()) {
            String n = names.getFirst();
            return n.startsWith("/") ? n.substring(1) : n;
        }
        return id == null ? "?" : id.substring(0, Math.min(12, id.length()));
    }
}
