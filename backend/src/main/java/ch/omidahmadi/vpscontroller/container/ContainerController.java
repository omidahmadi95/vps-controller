package ch.omidahmadi.vpscontroller.container;

import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ContainerController {

    public record ContainerReport(Instant checkedAt, List<ContainerView> containers) {
    }

    private final ContainerService service;

    public ContainerController(ContainerService service) {
        this.service = service;
    }

    @GetMapping("/containers")
    public ContainerReport containers() {
        return new ContainerReport(Instant.now(), service.containers());
    }
}
