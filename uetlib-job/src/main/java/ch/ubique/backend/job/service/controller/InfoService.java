package ch.ubique.backend.job.service.controller;

import ch.ubique.backend.job.model.client.*;

import com.github.zafarkhaja.semver.Version;
import com.google.common.collect.MoreCollectors;

import de.westemeyer.version.service.ArtifactVersionCollector;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@Profile("!job-run")
public class InfoService {

    private static final Set<Pair<String, String>> ARTIFACTS_TO_RETRIEVE =
            Set.of(
                    Pair.of("ch.ubique", "backend-jvm-lib-job"),
                    Pair.of("ch.admin.swisstopo.shared", "swisstopo-backend-shared-service"),
                    Pair.of("ch.sac.shared", "sac-backend-shared-service"));

    private static final List<Artifact> ARTIFACTS =
            ARTIFACTS_TO_RETRIEVE.stream()
                    .map(
                            pair -> {
                                final var groupId = pair.getLeft();
                                final var artifactId = pair.getRight();
                                final var version =
                                        Version.valueOf(
                                                ArtifactVersionCollector.findArtifact(
                                                                groupId, artifactId)
                                                        .getVersion());
                                return new Artifact(groupId, artifactId, version);
                            })
                    .sorted(
                            Comparator.comparing(Artifact::groupId)
                                    .thenComparing(Artifact::artifactId))
                    .toList();

    private final BuildProperties buildProperties;

    public InfoService(final BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    public InfoResponse getInfo() {
        final var mavenVersion = Version.valueOf(buildProperties.getVersion());
        final var backendLibVersion =
                ARTIFACTS.stream()
                        .filter(artifact -> "backend-jvm-lib-job".equals(artifact.artifactId()))
                        .map(Artifact::version)
                        .collect(MoreCollectors.onlyElement());
        return new InfoResponse(mavenVersion, backendLibVersion, ARTIFACTS);
    }
}
