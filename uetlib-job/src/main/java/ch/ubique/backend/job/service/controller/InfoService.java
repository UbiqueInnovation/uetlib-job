package ch.ubique.backend.job.service.controller;

import ch.ubique.backend.job.model.client.*;

import de.westemeyer.version.service.ArtifactVersionCollector;

import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

@Service
public class InfoService {

    private final BuildProperties buildProperties;

    public InfoService(final BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    public InfoResponse getInfo() {
        final var backendLibArtifact =
                ArtifactVersionCollector.findArtifact("ch.ubique", "backend-jvm-lib-job");
        return new InfoResponse(buildProperties.getVersion(), backendLibArtifact.getVersion());
    }
}
