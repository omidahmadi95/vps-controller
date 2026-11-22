package ch.omidahmadi.vpscontroller.check;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The status codes a check accepts, e.g. {@code ["200-399", "401"]}.
 * Rendering is canonical ({@code "200-399, 401"}) so it can be stored as one text column and shown in errors.
 */
public record StatusRanges(List<Range> ranges) {

    public record Range(int min, int max) {
    }

    public StatusRanges {
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("accept darf nicht leer sein");
        }
    }

    public static StatusRanges parse(List<String> tokens) {
        List<Range> ranges = new ArrayList<>();
        for (String token : tokens) {
            ranges.add(parseOne(token.trim()));
        }
        return new StatusRanges(List.copyOf(ranges));
    }

    /** Reads the canonical form produced by {@link #describe()} back in. */
    public static StatusRanges parseCsv(String csv) {
        return parse(List.of(csv.split(",")));
    }

    private static Range parseOne(String token) {
        if (token.isEmpty()) {
            throw new IllegalArgumentException("accept enthält einen leeren Eintrag");
        }
        int dash = token.indexOf('-');
        try {
            if (dash < 0) {
                int code = Integer.parseInt(token);
                checkBounds(code);
                return new Range(code, code);
            }
            int min = Integer.parseInt(token.substring(0, dash).trim());
            int max = Integer.parseInt(token.substring(dash + 1).trim());
            checkBounds(min);
            checkBounds(max);
            if (min > max) {
                throw new IllegalArgumentException("accept-Bereich " + token + " hat min > max");
            }
            return new Range(min, max);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("accept-Eintrag ist keine Zahl oder kein Bereich: " + token);
        }
    }

    private static void checkBounds(int code) {
        if (code < 100 || code > 599) {
            throw new IllegalArgumentException("accept-Code ausserhalb 100-599: " + code);
        }
    }

    public boolean accepts(int statusCode) {
        return ranges.stream().anyMatch(r -> statusCode >= r.min() && statusCode <= r.max());
    }

    public String describe() {
        return ranges.stream()
                .map(r -> r.min() == r.max() ? Integer.toString(r.min()) : r.min() + "-" + r.max())
                .collect(Collectors.joining(", "));
    }
}
