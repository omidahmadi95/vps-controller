package ch.omidahmadi.vpscontroller.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CheckMessagesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            45  | 45 s
            60  | 1 min
            720 | 12 min
            3600 | 1 h 0 min
            7500 | 2 h 5 min
            """)
    void humanFormatsDurations(long seconds, String expected) {
        assertThat(CheckMessages.human(Duration.ofSeconds(seconds))).isEqualTo(expected);
    }

    @Test
    void wentDownNamesTheCheckReasonAndTime() {
        String text = CheckMessages.wentDown("Shop", "HTTP 525 (erwartet 200-399)",
                Instant.parse("2026-11-23T09:41:00Z"), ZoneOffset.UTC);
        assertThat(text).isEqualTo("Ausgefallen: Shop – HTTP 525 (erwartet 200-399) (seit 09:41)");
    }

    @Test
    void recoveredNamesTheCheckAndOutageLength() {
        assertThat(CheckMessages.recovered("Shop", Duration.ofMinutes(12)))
                .isEqualTo("Wieder erreichbar: Shop nach 12 min");
    }
}
