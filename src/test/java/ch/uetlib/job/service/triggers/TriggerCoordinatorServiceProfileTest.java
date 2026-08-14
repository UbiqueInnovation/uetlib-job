package ch.uetlib.job.service.triggers;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Profiles;
import org.springframework.mock.env.MockEnvironment;

/**
 * Reads {@link TriggerCoordinatorService}'s {@code @Profile} via reflection rather than duplicating
 * the expression, so this stays coupled to the real annotation, and evaluates it the same way
 * {@code @Profile} does internally - {@code Environment#acceptsProfiles}.
 */
class TriggerCoordinatorServiceProfileTest {

    private static final String[] ANNOTATION_VALUE =
            TriggerCoordinatorService.class.getAnnotation(Profile.class).value();

    @Test
    void theAnnotationIsStillASingleAndExpressionNotAnOrArray() {
        // A multi-element array would be an OR, silently reintroducing the original bug.
        assertEquals(1, ANNOTATION_VALUE.length);
        assertEquals("!job-run & job-primary-triggers", ANNOTATION_VALUE[0]);
    }

    @Test
    void matchesOnAPrimaryInstanceWithTriggersEnabled() {
        assertTrue(matches("job-primary-triggers"));
    }

    @Test
    void doesNotMatchWithNeitherProfileActive() {
        assertFalse(matches());
    }

    @Test
    void doesNotMatchOnADedicatedJobRunPodEvenWithoutTriggersEnabled() {
        assertFalse(matches("job-run"));
    }

    @Test
    void doesNotMatchOnADedicatedJobRunPodEvenWithTriggersAlsoEnabled() {
        // The actual regression: job-primary-triggers can be set blanket-wide, even on --job runs.
        assertFalse(matches("job-run", "job-primary-triggers"));
    }

    private static boolean matches(final String... activeProfiles) {
        final var environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return environment.acceptsProfiles(Profiles.of(ANNOTATION_VALUE));
    }
}
