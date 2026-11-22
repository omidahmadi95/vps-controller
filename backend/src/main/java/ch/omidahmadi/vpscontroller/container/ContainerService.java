package ch.omidahmadi.vpscontroller.container;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ContainerService {

    private final DockerClient docker;
    private final ContainerStatusMapper mapper;

    public ContainerService(DockerClient docker, ContainerStatusMapper mapper) {
        this.docker = docker;
        this.mapper = mapper;
    }

    /** Most urgent first: broken, then uncertain, then intentionally stopped, then running. */
    private static final List<ContainerState> URGENCY = List.of(
            ContainerState.DEGRADED, ContainerState.UNREACHABLE, ContainerState.UNKNOWN,
            ContainerState.STOPPED, ContainerState.RUNNING);

    /** Sorted by urgency, then by name, so the list answers "what is wrong?" at a glance. */
    public List<ContainerView> containers() {
        return docker.listContainers().stream()
                .map(mapper::map)
                .sorted(Comparator.comparingInt((ContainerView v) -> URGENCY.indexOf(v.state()))
                        .thenComparing(ContainerView::name))
                .toList();
    }
}
