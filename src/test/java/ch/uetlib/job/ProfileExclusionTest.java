package ch.uetlib.job;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.mock.env.MockEnvironment;

class ProfileExclusionTest {

    @Test
    void removesOnlyTheGivenProfile() {
        final var result =
                ProfileExclusion.withoutProfile(
                        new String[] {"job-run", "job-primary-triggers", "no-security"},
                        "job-primary-triggers");

        assertArrayEquals(new String[] {"job-run", "no-security"}, result);
    }

    @Test
    void leavesTheArrayUnchangedWhenTheProfileIsNotPresent() {
        final var result =
                ProfileExclusion.withoutProfile(
                        new String[] {"job-run", "no-security"}, "job-primary-triggers");

        assertArrayEquals(new String[] {"job-run", "no-security"}, result);
    }

    @Test
    void excludingSetsTheEnvironmentsActiveProfilesWithoutTheGivenOne() {
        final var environment = new MockEnvironment();
        environment.setActiveProfiles("job-run", "job-primary-triggers");
        final var event =
                new ApplicationEnvironmentPreparedEvent(
                        mock(ConfigurableBootstrapContext.class),
                        mock(SpringApplication.class),
                        new String[0],
                        environment);

        ProfileExclusion.excluding("job-primary-triggers").onApplicationEvent(event);

        assertArrayEquals(new String[] {"job-run"}, environment.getActiveProfiles());
    }
}
