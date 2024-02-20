package ch.ubique.backend.job.k8s;

import static java.util.Objects.requireNonNull;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.k8s.KubernetesJobRunContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pivovarit.function.exception.WrappedException;

import io.kubernetes.client.openapi.models.V1Container;

import org.springframework.context.ApplicationContext;

import java.util.*;

public class KubernetesJobWrapper extends KubernetesJob {

    private final Job originalJob;

    public KubernetesJobWrapper(
            final ApplicationContext applicationContext, final Job originalJob) {
        super(applicationContext);
        this.originalJob = originalJob;
    }

    @Override
    protected Collection<V1Container> containers(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        final var currentPodContainer = getCurrentPodContainer(kubernetesJobRunContext);

        final var args =
                new ArrayList<>(
                        List.of(
                                "--",
                                "--job",
                                originalJob.getClass().getName(),
                                "--run",
                                String.valueOf(
                                        kubernetesJobRunContext.jobRunContext().jobRun().id())));
        if (kubernetesJobRunContext.jobRunContext().arguments().forceRun()) {
            args.add("--force");
        }

        final var additionalData =
                kubernetesJobRunContext.jobRunContext().arguments().additionalData();
        if (additionalData != null) {
            args.add("--additionalData");
            args.add(serializeObject(additionalData));
            args.add("--additionalDataClass");
            args.add(additionalData.getClass().getCanonicalName());
        }

        return Set.of(
                new V1Container()
                        .name("job-image")
                        .image(currentPodContainer.getImage())
                        .args(args)
                        .env(currentPodContainer.getEnv())
                        .addEnvItem(additionalJavaOptsEnvVar(kubernetesJobRunContext))
                        .envFrom(currentPodContainer.getEnvFrom())
                        .volumeMounts(currentPodContainer.getVolumeMounts())
                        .imagePullPolicy(currentPodContainer.getImagePullPolicy())
                        .resources(resources(kubernetesJobRunContext)));
    }

    private static V1Container getCurrentPodContainer(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        final var currentPod =
                requireNonNull(kubernetesJobRunContext.kubernetesContext().currentPod());
        final var currentPodSpec = requireNonNull(currentPod.getSpec());

        // Assume there is only one container
        if (currentPodSpec.getContainers().size() != 1) {
            throw new IllegalArgumentException(
                    "Expected current importer pod to have exactly one container. This is a"
                            + " simplification of the current implementation. If needed, you can modify"
                            + " this code to drop this restriction.");
        }

        return currentPodSpec.getContainers().getFirst();
    }

    private String serializeObject(final Object object) {
        try {
            return applicationContext.getBean(ObjectMapper.class).writeValueAsString(object);
        } catch (final JsonProcessingException e) {
            throw new WrappedException(e);
        }
    }
}
