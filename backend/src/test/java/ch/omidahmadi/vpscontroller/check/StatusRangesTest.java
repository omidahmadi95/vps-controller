package ch.omidahmadi.vpscontroller.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StatusRangesTest {

    @Test
    void defaultRangeAcceptsTwoHundredToThreeNinetyNine() {
        StatusRanges ranges = StatusRanges.parse(java.util.List.of("200-399"));
        assertThat(ranges.accepts(200)).isTrue();
        assertThat(ranges.accepts(399)).isTrue();
        assertThat(ranges.accepts(400)).isFalse();
        assertThat(ranges.accepts(199)).isFalse();
    }

    @Test
    void singleCodeAndRangeCanBeMixed() {
        StatusRanges ranges = StatusRanges.parse(java.util.List.of("200-299", "401"));
        assertThat(ranges.accepts(299)).isTrue();
        assertThat(ranges.accepts(401)).isTrue();
        assertThat(ranges.accepts(300)).isFalse();
        assertThat(ranges.describe()).isEqualTo("200-299, 401");
    }

    @Test
    void describeAndParseCsvRoundTrip() {
        StatusRanges ranges = StatusRanges.parse(java.util.List.of("200-399", "401"));
        assertThat(StatusRanges.parseCsv(ranges.describe()).ranges()).isEqualTo(ranges.ranges());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "99", "600", "300-200", "20x-300"})
    void rejectsBadTokens(String token) {
        assertThatThrownBy(() -> StatusRanges.parse(java.util.List.of(token)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyList() {
        assertThatThrownBy(() -> new StatusRanges(java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
