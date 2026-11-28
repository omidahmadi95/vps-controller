package ch.omidahmadi.vpscontroller.overview;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OverviewController.class)
class OverviewControllerTest {

    private static final Instant CHECKED_AT = Instant.parse("2026-11-28T07:12:04Z");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OverviewService service;

    @Test
    void serialisesTheContractExactly() throws Exception {
        given(service.overview()).willReturn(new Overview(
                CHECKED_AT,
                OverviewStatus.PROBLEM,
                new Overview.Counts(1, 0, 11, 2, 1),
                List.of(new Overview.Incident(Overview.Incident.Kind.CHECK, Overview.Incident.Severity.PROBLEM,
                        "api.example.org", "DOWN", "Ausgefallen", Instant.parse("2026-11-28T07:08:31Z"),
                        "HTTP 503 (erwartet 200-399)", 7L)),
                List.of(
                        new Overview.Source("checks", SourceState.OK, "Aktuell", Instant.parse("2026-11-28T07:11:58Z")),
                        new Overview.Source("containers", SourceState.OK, "Aktuell", CHECKED_AT),
                        new Overview.Source("heartbeat", SourceState.NOT_CONFIGURED, "Nicht eingerichtet", null)),
                List.of(new Overview.RecentOutage(3L, "shop.example.org", Instant.parse("2026-11-27T04:33:54Z"),
                        Instant.parse("2026-11-27T04:35:55Z"), 121L, 2))));

        mvc.perform(get("/api/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedAt").value("2026-11-28T07:12:04Z"))
                .andExpect(jsonPath("$.status").value("PROBLEM"))
                .andExpect(jsonPath("$.counts.problem").value(1))
                .andExpect(jsonPath("$.counts.uncertain").value(0))
                .andExpect(jsonPath("$.counts.ok").value(11))
                .andExpect(jsonPath("$.counts.stopped").value(2))
                .andExpect(jsonPath("$.counts.paused").value(1))
                .andExpect(jsonPath("$.incidents[0].kind").value("CHECK"))
                .andExpect(jsonPath("$.incidents[0].severity").value("PROBLEM"))
                .andExpect(jsonPath("$.incidents[0].name").value("api.example.org"))
                .andExpect(jsonPath("$.incidents[0].state").value("DOWN"))
                .andExpect(jsonPath("$.incidents[0].label").value("Ausgefallen"))
                .andExpect(jsonPath("$.incidents[0].since").value("2026-11-28T07:08:31Z"))
                .andExpect(jsonPath("$.incidents[0].detail").value("HTTP 503 (erwartet 200-399)"))
                .andExpect(jsonPath("$.incidents[0].checkId").value(7))
                .andExpect(jsonPath("$.sources[0].name").value("checks"))
                .andExpect(jsonPath("$.sources[0].state").value("OK"))
                .andExpect(jsonPath("$.sources[0].label").value("Aktuell"))
                .andExpect(jsonPath("$.sources[0].lastSeenAt").value("2026-11-28T07:11:58Z"))
                .andExpect(jsonPath("$.sources[1].name").value("containers"))
                .andExpect(jsonPath("$.sources[2].name").value("heartbeat"))
                .andExpect(jsonPath("$.sources[2].state").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.sources[2].label").value("Nicht eingerichtet"))
                .andExpect(jsonPath("$.sources[2].lastSeenAt").isEmpty())
                .andExpect(jsonPath("$.recentOutages[0].checkId").value(3))
                .andExpect(jsonPath("$.recentOutages[0].name").value("shop.example.org"))
                .andExpect(jsonPath("$.recentOutages[0].start").value("2026-11-27T04:33:54Z"))
                .andExpect(jsonPath("$.recentOutages[0].end").value("2026-11-27T04:35:55Z"))
                .andExpect(jsonPath("$.recentOutages[0].durationSeconds").value(121))
                .andExpect(jsonPath("$.recentOutages[0].failures").value(2));
    }

    @Test
    void nonCheckIncidentHasANullCheckId() throws Exception {
        given(service.overview()).willReturn(new Overview(
                CHECKED_AT,
                OverviewStatus.UNCERTAIN,
                new Overview.Counts(0, 1, 0, 0, 0),
                List.of(new Overview.Incident(Overview.Incident.Kind.CONTAINER, Overview.Incident.Severity.UNCERTAIN,
                        "wiki-1", "UNKNOWN", "Unbekannt", null, "Health-Check läuft noch", null)),
                List.of(new Overview.Source("containers", SourceState.UNREACHABLE, "Nicht erreichbar", null)),
                List.of()));

        mvc.perform(get("/api/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidents[0].checkId").isEmpty())
                .andExpect(jsonPath("$.incidents[0].since").isEmpty())
                .andExpect(jsonPath("$.recentOutages").isEmpty());
    }
}
