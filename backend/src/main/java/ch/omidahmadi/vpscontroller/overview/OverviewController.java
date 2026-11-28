package ch.omidahmadi.vpscontroller.overview;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OverviewController {

    private final OverviewService service;

    public OverviewController(OverviewService service) {
        this.service = service;
    }

    /** Always 200: a failing source degrades the answer, it never fails the endpoint. */
    @GetMapping("/overview")
    public Overview overview() {
        return service.overview();
    }
}
