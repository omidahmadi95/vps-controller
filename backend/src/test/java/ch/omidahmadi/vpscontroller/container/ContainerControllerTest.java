package ch.omidahmadi.vpscontroller.container;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ContainerController.class)
class ContainerControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ContainerService service;

    @Test
    void listsContainersWithGermanLabels() throws Exception {
        given(service.containers()).willReturn(List.of(
                ContainerView.of("api", "app:1", ContainerState.DEGRADED, "Health-Check schlägt fehl")));

        mvc.perform(get("/api/containers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.containers[0].name").value("api"))
                .andExpect(jsonPath("$.containers[0].state").value("DEGRADED"))
                .andExpect(jsonPath("$.containers[0].label").value("Gestört"))
                .andExpect(jsonPath("$.checkedAt").exists());
    }

    @Test
    void unreachableSourceIs503ProblemNotAnEmptyList() throws Exception {
        given(service.containers()).willThrow(new SourceUnreachableException("Docker-Quelle nicht erreichbar: refused", null));

        mvc.perform(get("/api/containers"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Nicht erreichbar"))
                .andExpect(jsonPath("$.state").value("UNREACHABLE"))
                .andExpect(jsonPath("$.containers").doesNotExist());
    }
}
