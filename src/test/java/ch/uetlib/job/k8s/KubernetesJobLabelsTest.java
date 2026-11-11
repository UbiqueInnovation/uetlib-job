package ch.uetlib.job.k8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.uetlib.job.model.JobRunContext;
import ch.uetlib.job.model.k8s.KubernetesContext;
import ch.uetlib.job.model.k8s.KubernetesJobRunContext;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.properties.JobProperty;
import ch.uetlib.job.model.properties.JobPropertyDefinitions;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collection;
import java.util.List;
import java.util.Map;

class KubernetesJobLabelsTest {

    private static final Map<String, String> CURRENT_POD_LABELS =
            Map.of(
                    "app", "my-app",
                    "egress", "cilium",
                    "team", "backend",
                    "pod-template-hash", "5d8f7c9b6");

    @Test
    void doesNotInheritCurrentPodLabelsByDefault() {
        final var labels = new TestJob(false).labels(context(CURRENT_POD_LABELS, null));

        assertEquals(Map.of("app", "my-app-job"), labels);
    }

    @Test
    void inheritsCurrentPodLabelsWithoutPodSpecificOnesWhenEnabled() {
        final var labels = new TestJob(true).labels(context(CURRENT_POD_LABELS, null));

        assertEquals(Map.of("app", "my-app-job", "egress", "cilium", "team", "backend"), labels);
    }

    @Test
    void podLabelsPropertyOverridesInheritedLabels() {
        final var labels =
                new TestJob(true)
                        .labels(
                                context(
                                        CURRENT_POD_LABELS,
                                        Map.of("team", "data", "extra", "yes")));

        assertEquals(
                Map.of(
                        "app", "my-app-job",
                        "egress", "cilium",
                        "team", "data",
                        "extra", "yes"),
                labels);
    }

    @Test
    void inheritsNothingWhenNotRunningInKubernetes() {
        final var labels = new TestJob(true).labels(context(null, null));

        assertEquals(Map.of("app", "my-app-job"), labels);
    }

    private static KubernetesJobRunContext context(
            final @Nullable Map<String, String> currentPodLabels,
            final @Nullable Map<String, String> podLabelsProperty) {
        final List<JobProperty<Object>> properties =
                podLabelsProperty == null
                        ? List.of()
                        : List.of(
                                castProperty(
                                        JobPropertyDefinitions.K8S_POD_LABELS.createProperty(
                                                podLabelsProperty)));
        final var jobRunContext =
                new JobRunContext(null, null, null, new JobProperties(properties));
        final var currentPod =
                currentPodLabels == null
                        ? null
                        : new V1Pod().metadata(new V1ObjectMeta().labels(currentPodLabels));
        return new KubernetesJobRunContext(
                jobRunContext,
                new KubernetesContext(mock(ApiClient.class), "test-namespace", currentPod));
    }

    @SuppressWarnings("unchecked")
    private static JobProperty<Object> castProperty(final JobProperty<?> property) {
        return (JobProperty<Object>) property;
    }

    private static ApplicationContext applicationContext() {
        final var applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getEnvironment())
                .thenReturn(
                        new MockEnvironment().withProperty("spring.application.name", "my-app"));
        return applicationContext;
    }

    private static final class TestJob extends KubernetesJob {

        private final boolean inheritCurrentPodLabels;

        private TestJob(final boolean inheritCurrentPodLabels) {
            super(applicationContext());
            this.inheritCurrentPodLabels = inheritCurrentPodLabels;
        }

        @Override
        protected boolean inheritCurrentPodLabels() {
            return inheritCurrentPodLabels;
        }

        @Override
        protected Collection<V1Container> containers(
                final KubernetesJobRunContext kubernetesJobRunContext) {
            return List.of();
        }
    }
}
