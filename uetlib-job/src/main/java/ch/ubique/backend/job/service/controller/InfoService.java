package ch.ubique.backend.job.service.controller;

import ch.ubique.backend.job.model.client.*;

import com.github.zafarkhaja.semver.Version;

import de.westemeyer.version.service.ArtifactVersionCollector;

import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!job-run")
public class InfoService {

    private final BuildProperties buildProperties;

    public InfoService(final BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    public InfoResponse getInfo() {
        final var mavenVersion = Version.valueOf(buildProperties.getVersion());
        final var backendLibVersion =
                Version.valueOf(
                        ArtifactVersionCollector.findArtifact("ch.ubique", "backend-jvm-lib-job")
                                .getVersion());
        final var swisstopoBackendSharedVersion =
                Version.valueOf(
                        ArtifactVersionCollector.findArtifact(
                                        "ch.admin.swisstopo.shared",
                                        "swisstopo-backend-shared-service")
                                .getVersion());
        final var sacBackendSharedVersion =
                Version.valueOf(
                        ArtifactVersionCollector.findArtifact(
                                        "ch.sac.shared", "sac-backend-shared-service")
                                .getVersion());
        return new InfoResponse(
                mavenVersion,
                backendLibVersion,
                swisstopoBackendSharedVersion,
                sacBackendSharedVersion);
    }
}
