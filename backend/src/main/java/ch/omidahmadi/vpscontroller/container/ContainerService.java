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

    /** Problems first, then by name, so the list answers "what is wrong?" at a glance. */
    public List<ContainerView> containers() {
        return docker.listContainers().stream()
                .map(mapper::map)
                .sorted(Comparator.comparing((ContainerView v) -> v.state() == ContainerState.RUNNING)
                        .thenComparing(ContainerView::name))
                .toList();
    }
}
