package ch.omidahmadi.vpscontroller.check;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CheckController.class)
class CheckControllerTest {

    private static final Instant FIXED = Instant.parse("2026-11-23T10:00:00Z");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CheckRepository repository;

    @MockitoBean
    Clock clock;

    @Test
    void listsChecksSortedByUrgencyThenName() throws Exception {
        given(clock.instant()).willReturn(FIXED);
        given(repository.overview(FIXED)).willReturn(List.of(
                view(2, "Zulu", CheckState.PAUSED),
                view(1, "Alpha", CheckState.UP),
                view(3, "Shop", CheckState.DOWN)));

        mvc.perform(get("/api/checks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedAt").value("2026-11-23T10:00:00Z"))
                .andExpect(jsonPath("$.checks[0].name").value("Shop"))
                .andExpect(jsonPath("$.checks[0].state").value("DOWN"))
                .andExpect(jsonPath("$.checks[0].label").value("Ausgefallen"))
                .andExpect(jsonPath("$.checks[0].lastStatusCode").value(525))
                .andExpect(jsonPath("$.checks[0].lastLatencyMs").isEmpty())
                .andExpect(jsonPath("$.checks[0].lastError").value("HTTP 525 (erwartet 200-399)"))
                .andExpect(jsonPath("$.checks[0].uptime24h").value(0.0))
                .andExpect(jsonPath("$.checks[0].uptime30d").value(61.3))
                .andExpect(jsonPath("$.checks[1].name").value("Alpha"))
                .andExpect(jsonPath("$.checks[2].name").value("Zulu"))
                .andExpect(jsonPath("$.checks[2].state").value("PAUSED"));
    }

    @Test
    void historyReturnsFixedThirtyMinuteWindows() throws Exception {
        long base = FIXED.getEpochSecond();
        given(clock.instant()).willReturn(FIXED);
        given(repository.exists(3)).willReturn(true);
        given(repository.buckets(eq(3L), any()))
                .willReturn(List.of(new CheckRepository.Bucket(base, 30, 0, 81.0)));

        mvc.perform(get("/api/checks/3/history").param("hours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkId").value(3))
                .andExpect(jsonPath("$.from").value("2026-11-22T10:30:00Z"))
                .andExpect(jsonPath("$.to").value("2026-11-23T10:00:00Z"))
                .andExpect(jsonPath("$.buckets.length()").value(48))
                .andExpect(jsonPath("$.buckets[0].total").value(0))
                .andExpect(jsonPath("$.buckets[0].avgLatencyMs").isEmpty())
                .andExpect(jsonPath("$.buckets[47].start").value("2026-11-23T10:00:00Z"))
                .andExpect(jsonPath("$.buckets[47].total").value(30))
                .andExpect(jsonPath("$.buckets[47].avgLatencyMs").value(81));
    }

    @Test
    void unknownIdIsAProblemDetail404() throws Exception {
        given(repository.exists(99)).willReturn(false);

        mvc.perform(get("/api/checks/99/history"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Nicht gefunden"))
                .andExpect(jsonPath("$.detail").value("Check 99 existiert nicht"));
    }

    @Test
    void outOfRangeHoursIsAProblemDetail400() throws Exception {
        mvc.perform(get("/api/checks/3/history").param("hours", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"));
    }

    private static CheckView view(long id, String name, CheckState state) {
        return CheckView.of(id, name, "https://" + name.toLowerCase() + ".example.org/health", state,
                FIXED.minusSeconds(60), FIXED.minusSeconds(30), 525, null, "HTTP 525 (erwartet 200-399)", 0.0, 61.3);
    }
}
