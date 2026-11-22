package ch.omidahmadi.vpscontroller.check;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Reads the checks YAML (property {@code vps.checks.file}). A missing file means zero checks, not a failure;
 * a single bad entry is skipped with a warning so one typo cannot take the whole controller down.
 */
@Component
public class ChecksFileLoader {

    private static final Logger log = LoggerFactory.getLogger(ChecksFileLoader.class);

    private final String path;

    public ChecksFileLoader(@Value("${vps.checks.file:./checks.yml}") String path) {
        this.path = path;
    }

    public List<CheckSpec> load() {
        Path file = Path.of(path);
        if (!Files.isRegularFile(file)) {
            log.warn("Keine Checks-Datei unter {} gefunden – 0 Checks aktiv", file.toAbsolutePath());
            return List.of();
        }
        try (InputStream in = Files.newInputStream(file)) {
            // shortcut: our own trusted config file, so a plain map parse is enough; revisit if it ever becomes user input.
            Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            return parse(root);
        } catch (Exception e) {
            log.warn("Checks-Datei {} konnte nicht gelesen werden: {} – 0 Checks aktiv", file,
                    e.getClass().getSimpleName());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<CheckSpec> parse(Object root) {
        List<CheckSpec> specs = new ArrayList<>();
        if (!(root instanceof Map<?, ?> map) || !(map.get("checks") instanceof List<?> entries)) {
            log.warn("{} enthält keinen 'checks'-Block – 0 Checks aktiv", path);
            return List.of();
        }
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> m)) {
                log.warn("Überspringe ungültigen Check-Eintrag in {}", path);
                continue;
            }
            try {
                specs.add(toSpec((Map<String, Object>) m));
            } catch (RuntimeException e) {
                log.warn("Überspringe ungültigen Check-Eintrag in {}: {}", path, e.getMessage());
            }
        }
        return specs;
    }

    private CheckSpec toSpec(Map<String, Object> m) {
        return new CheckSpec(
                str(m.get("name")),
                str(m.get("url")),
                duration(m.get("interval"), Duration.ofSeconds(60)),
                duration(m.get("timeout"), Duration.ofSeconds(10)),
                accept(m.get("accept")),
                integer(m.get("failureThreshold"), 2),
                integer(m.get("recoveryThreshold"), 1));
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static int integer(Object v, int fallback) {
        if (v == null) {
            return fallback;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(v.toString().trim());
    }

    private static StatusRanges accept(Object v) {
        if (v == null) {
            return StatusRanges.parse(List.of("200-399"));
        }
        if (v instanceof List<?> list) {
            return StatusRanges.parse(list.stream().map(Object::toString).toList());
        }
        return StatusRanges.parse(List.of(v.toString()));
    }

    static Duration duration(Object v, Duration fallback) {
        if (v == null) {
            return fallback;
        }
        if (v instanceof Number n) {
            return Duration.ofSeconds(n.longValue());
        }
        String s = v.toString().trim().toLowerCase();
        if (s.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(s.substring(0, s.length() - 2).trim()));
        }
        if (s.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(s.substring(0, s.length() - 1).trim()));
        }
        if (s.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(s.substring(0, s.length() - 1).trim()));
        }
        if (s.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(s.substring(0, s.length() - 1).trim()));
        }
        return Duration.ofSeconds(Long.parseLong(s));
    }
}
