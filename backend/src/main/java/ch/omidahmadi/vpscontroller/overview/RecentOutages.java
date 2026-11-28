package ch.omidahmadi.vpscontroller.overview;

import ch.omidahmadi.vpscontroller.check.CheckRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rule 8: the last seven days of real interruptions. A run of consecutive failed results counts only when it
 * reaches the check's failure threshold — the same debounce as alerting, so single blips never appear.
 */
final class RecentOutages {

    private static final int MAX = 20;

    private RecentOutages() {
    }

    /** {@code rows} must be ordered by check, then time. {@code end} is the first success after the run, null while ongoing. */
    static List<Overview.RecentOutage> of(List<CheckRepository.OverviewResult> rows, Instant now) {
        Map<Long, List<CheckRepository.OverviewResult>> byCheck = new LinkedHashMap<>();
        for (CheckRepository.OverviewResult row : rows) {
            byCheck.computeIfAbsent(row.checkId(), id -> new ArrayList<>()).add(row);
        }

        List<Overview.RecentOutage> outages = new ArrayList<>();
        for (List<CheckRepository.OverviewResult> series : byCheck.values()) {
            int threshold = series.getFirst().failureThreshold();
            int i = 0;
            while (i < series.size()) {
                if (series.get(i).ok()) {
                    i++;
                    continue;
                }
                int start = i;
                while (i < series.size() && !series.get(i).ok()) {
                    i++;
                }
                int failures = i - start;
                if (failures >= threshold) {
                    Instant startAt = series.get(start).checkedAt();
                    Instant endAt = i < series.size() ? series.get(i).checkedAt() : null;
                    long seconds = Duration.between(startAt, endAt != null ? endAt : now).getSeconds();
                    outages.add(new Overview.RecentOutage(series.get(start).checkId(), series.get(start).name(),
                            startAt, endAt, seconds, failures));
                }
            }
        }

        outages.sort(Comparator.comparing(Overview.RecentOutage::start).reversed());
        return outages.size() > MAX ? List.copyOf(outages.subList(0, MAX)) : outages;
    }
}
