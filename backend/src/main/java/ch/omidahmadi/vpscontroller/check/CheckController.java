package ch.omidahmadi.vpscontroller.check;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CheckController {

    /** Most urgent first, then by name. */
    private static final List<CheckState> URGENCY =
            List.of(CheckState.DOWN, CheckState.PENDING, CheckState.UP, CheckState.PAUSED);

    private static final int BUCKET_SECONDS = 1800;

    public record CheckReport(Instant checkedAt, List<CheckView> checks) {
    }

    public record BucketView(Instant start, int total, int failed, Long avgLatencyMs) {
    }

    public record HistoryView(long checkId, Instant from, Instant to, List<BucketView> buckets) {
    }

    private final CheckRepository repository;
    private final Clock clock;

    public CheckController(CheckRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @GetMapping("/checks")
    public CheckReport checks() {
        Instant now = clock.instant();
        List<CheckView> sorted = repository.overview(now).stream()
                .sorted(Comparator.comparingInt((CheckView v) -> URGENCY.indexOf(v.state()))
                        .thenComparing(CheckView::name))
                .toList();
        return new CheckReport(now, sorted);
    }

    @GetMapping("/checks/{id}/history")
    public HistoryView history(@PathVariable long id, @RequestParam(defaultValue = "24") int hours) {
        if (hours < 1 || hours > 720) {
            throw new IllegalArgumentException("hours muss zwischen 1 und 720 liegen");
        }
        if (!repository.exists(id)) {
            throw new CheckNotFoundException(id);
        }
        Instant now = clock.instant();
        long count = hours * 2L;
        long base = now.getEpochSecond() / BUCKET_SECONDS * BUCKET_SECONDS;
        Instant from = Instant.ofEpochSecond(base - (count - 1) * BUCKET_SECONDS);

        Map<Long, CheckRepository.Bucket> byStart = repository.buckets(id, from).stream()
                .collect(Collectors.toMap(CheckRepository.Bucket::startEpochSeconds, Function.identity()));

        List<BucketView> buckets = new ArrayList<>();
        for (long i = 0; i < count; i++) {
            long start = base - (count - 1 - i) * BUCKET_SECONDS;
            CheckRepository.Bucket b = byStart.get(start);
            buckets.add(b == null
                    ? new BucketView(Instant.ofEpochSecond(start), 0, 0, null)
                    : new BucketView(Instant.ofEpochSecond(start), b.total(), b.failed(),
                            b.avgLatencyMs() == null ? null : Math.round(b.avgLatencyMs())));
        }
        return new HistoryView(id, from, now, buckets);
    }
}
