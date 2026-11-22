package ch.omidahmadi.vpscontroller.container;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ContainerStatusMapperTest {

    private final ContainerStatusMapper mapper = new ContainerStatusMapper();

    @ParameterizedTest(name = "{0} / \"{1}\" -> {2}")
    @CsvSource(delimiter = '|', nullValues = "NULL", textBlock = """
            running    | Up 4 days (healthy)            | RUNNING
            running    | Up 4 days                      | RUNNING
            running    | Up 3 days (unhealthy)          | DEGRADED
            running    | Up 5 seconds (health: starting)| UNKNOWN
            restarting | Restarting (1) 3 seconds ago   | DEGRADED
            exited     | Exited (0) 2 hours ago         | STOPPED
            exited     | Exited (137) 5 minutes ago     | DEGRADED
            exited     | Exited (-1) 1 minute ago       | DEGRADED
            exited     | Exited                         | UNKNOWN
            dead       | Dead                           | DEGRADED
            paused     | Up 2 hours (Paused)            | STOPPED
            created    | Created                        | STOPPED
            removing   | Removal In Progress            | UNKNOWN
            weird      | ?                              | UNKNOWN
            NULL       | NULL                           | UNKNOWN
            RUNNING    | Up 1 hour (healthy)            | RUNNING
            """)
    void mapsDockerStateToOneUnambiguousState(String state, String status, ContainerState expected) {
        ContainerView view = mapper.map(new DockerContainer("abc", List.of("/web"), "nginx", state, status));

        assertThat(view.state()).isEqualTo(expected);
        assertThat(view.label()).isEqualTo(expected.label());
    }

    @Test
    void onlyAProvenRunningContainerIsRunning() {
        for (String status : new String[] {"Up 1 hour (unhealthy)", "Up 1 s (health: starting)"}) {
            assertThat(mapper.map(new DockerContainer("x", List.of("/a"), "img", "running", status)).state())
                    .isNotEqualTo(ContainerState.RUNNING);
        }
    }

    @Test
    void nameDropsDockersLeadingSlashAndFallsBackToShortId() {
        assertThat(ContainerStatusMapper.displayName(List.of("/caddy"), "x")).isEqualTo("caddy");
        assertThat(ContainerStatusMapper.displayName(List.of(), "0123456789abcdef")).isEqualTo("0123456789ab");
        assertThat(ContainerStatusMapper.displayName(null, null)).isEqualTo("?");
    }
}
