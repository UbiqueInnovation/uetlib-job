package ch.ubique.uetlib.job.k8s;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import ch.ubique.uetlib.job.Job;
import ch.ubique.uetlib.job.model.k8s.KubernetesJobRunContext;

import com.google.common.base.CaseFormat;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1VolumeMount;

import org.apache.commons.lang3.exception.UncheckedException;
import org.springframework.context.ApplicationContext;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.stream.Collectors;

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
        return Set.of(mainContainer(kubernetesJobRunContext));
    }

    @Override
    public boolean watchJob() {
        return false;
    }

    protected V1Container mainContainer(final KubernetesJobRunContext kubernetesJobRunContext) {
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

        final var container =
                new V1Container()
                        .name("job-image")
                        .image(currentPodContainer.getImage())
                        .args(args)
                        .envFrom(currentPodContainer.getEnvFrom())
                        .volumeMounts(mainContainerVolumeMounts(kubernetesJobRunContext))
                        .imagePullPolicy(currentPodContainer.getImagePullPolicy())
                        .resources(resources(kubernetesJobRunContext));

        final var envVars = requireNonNullElse(currentPodContainer.getEnv(), List.<V1EnvVar>of());
        final var additionalJavaOptsEnvVar = additionalJavaOptsEnvVar(kubernetesJobRunContext);
        if (additionalJavaOptsEnvVar == null) {
            container.env(envVars);
        } else {
            final var filteredEnv =
                    envVars.stream()
                            .filter(env -> !"ADDITIONAL_JAVA_OPTS".equals(env.getName()))
                            .collect(Collectors.toCollection(ArrayList::new));
            container.env(filteredEnv).addEnvItem(additionalJavaOptsEnvVar);
        }

        return container;
    }

    protected List<V1VolumeMount> mainContainerVolumeMounts(
            final KubernetesJobRunContext kubernetesJobRunContext) {
        final var currentPodContainer = getCurrentPodContainer(kubernetesJobRunContext);
        return requireNonNullElse(currentPodContainer.getVolumeMounts(), List.of());
    }

    @Override
    protected Map<String, String> labels(final KubernetesJobRunContext kubernetesJobRunContext) {
        final var labels = new HashMap<String, String>();
        final var jobClassName = originalJob.getClass().getSimpleName();
        labels.put("job", CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, jobClassName));
        labels.putAll(super.labels(kubernetesJobRunContext));
        return labels;
    }

    protected static V1Container getCurrentPodContainer(
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
            return applicationContext.getBean(JsonMapper.class).writeValueAsString(object);
        } catch (final JacksonException e) {
            throw new UncheckedException(e);
        }
    }
}
