package ch.uetlib.job.annotation;

import static org.junit.jupiter.api.Assertions.*;

import ch.uetlib.job.cleanup.CleanupJob;
import ch.uetlib.job.health.HealthCheckJob;
import ch.uetlib.job.testjob.TestJob;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * {@link EnableJobs} scans the library's own jobs only when asked; checked against {@link
 * RegisteredJobDefinitions}.
 */
class EnableJobsScanningTest {

    @Configuration
    @EnableJobs({"ch.uetlib.job.health", "ch.uetlib.job.cleanup"})
    static class TheTwoDefaultJobPackages {}

    @Configuration
    @EnableJobs("ch.uetlib.job")
    static class TheWholeLibraryPackage {}

    @Configuration
    @EnableJobs("ch.uetlib.job.testjob")
    static class OnlyTheTestJobPackage {}

    @Test
    void namingTheTwoDefaultJobPackagesPicksUpExactlyThoseTwoJobs() {
        assertEquals(
                Set.of(HealthCheckJob.class, CleanupJob.class),
                registeredJobClasses(TheTwoDefaultJobPackages.class));
    }

    @Test
    void namingTheLibraryPackagePicksUpBothDefaultJobsAmongTheRest() {
        // A project listing the umbrella package gets them too, alongside whatever else it scans.
        final var registered = registeredJobClasses(TheWholeLibraryPackage.class);

        assertTrue(registered.contains(HealthCheckJob.class));
        assertTrue(registered.contains(CleanupJob.class));
    }

    @Test
    void aProjectThatNamesNeitherPackageGetsNeitherDefaultJob() {
        final var registered = registeredJobClasses(OnlyTheTestJobPackage.class);

        assertTrue(registered.contains(TestJob.class), "its own jobs are still scanned");
        assertFalse(registered.contains(HealthCheckJob.class));
        assertFalse(registered.contains(CleanupJob.class));
    }

    private static Set<Class<?>> registeredJobClasses(final Class<?> enableJobsConfig) {
        try (final var context = new AnnotationConfigApplicationContext(enableJobsConfig)) {
            return context.getBean(RegisteredJobDefinitions.class).registeredJobClasses();
        }
    }
}
