package ch.uetlib.job.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

/** Scheduling must be active on every instance, not just the primary - it once silently wasn't. */
class UetlibJobSchedulingConfigTest {

    @Test
    void schedulingIsEnabledOnAPrimaryInstance() {
        assertTrue(schedulingEnabledWith("test", "job-primary-triggers"));
    }

    @Test
    void schedulingIsEnabledOnANonPrimaryInstance() {
        // The regression: these instances still dispatch the queue and reconcile it.
        assertTrue(schedulingEnabledWith("test"));
    }

    @Test
    void schedulingIsNotEnabledOnADedicatedJobRunProcess() {
        assertFalse(schedulingEnabledWith("test", "job-run"));
    }

    private static boolean schedulingEnabledWith(final String... activeProfiles) {
        try (final var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(activeProfiles);
            context.register(UetlibJobSchedulingConfig.class);
            context.refresh();
            return context.getBeanFactory()
                    .containsBeanDefinition(
                            TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
        }
    }
}
