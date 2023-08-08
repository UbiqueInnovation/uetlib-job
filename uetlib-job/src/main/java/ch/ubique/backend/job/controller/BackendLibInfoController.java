package ch.ubique.backend.job.controller;

import ch.ubique.backend.job.model.client.InfoResponse;
import ch.ubique.backend.job.service.controller.InfoService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/info")
@CrossOrigin({
    "https://status.systems.ubique.ch",
    "https://status-dev.systems.ubique.ch",
    "http://localhost:3000"
})
public class BackendLibInfoController {

    private final InfoService infoService;

    public BackendLibInfoController(final InfoService infoService) {
        this.infoService = infoService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public InfoResponse info() {
        return infoService.getInfo();
    }
}
