package ch.omidahmadi.vpscontroller.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContainerServiceTest {

    @Test
    void brokenThenUncertainThenStoppedThenRunningThenByName() {
        DockerClient docker = mock(DockerClient.class);
        when(docker.listContainers()).thenReturn(List.of(
                new DockerContainer("1", List.of("/b-ok"), "i", "running", "Up 1 day"),
                new DockerContainer("2", List.of("/a-stopped"), "i", "exited", "Exited (0) 1 min ago"),
                new DockerContainer("3", List.of("/z-broken"), "i", "exited", "Exited (1) 1 min ago"),
                new DockerContainer("4", List.of("/y-starting"), "i", "running", "Up 2 s (health: starting)"),
                new DockerContainer("5", List.of("/a-ok"), "i", "running", "Up 1 day (healthy)")));

        List<String> names = new ContainerService(docker, new ContainerStatusMapper()).containers().stream()
                .map(ContainerView::name).toList();

        assertThat(names).containsExactly("z-broken", "y-starting", "a-stopped", "a-ok", "b-ok");
    }
}
