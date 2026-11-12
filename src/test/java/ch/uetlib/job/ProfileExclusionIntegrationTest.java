package ch.uetlib.job;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * {@link ProfileExclusionTest} only proves the listener's filtering logic; this proves it wins the
 * ordering race against Spring Boot's own profile activation, i.e. isn't a no-op in practice.
 */
class ProfileExclusionIntegrationTest {

    private static final String PROFILE = "job-primary-triggers";

    // A command-line arg outranks surefire's own -Dspring.profiles.active (see pom.xml), so this
    // reliably proves the profile is genuinely active before the listener runs.
    private static final String ACTIVATE_PROFILE_ARG = "--spring.profiles.active=" + PROFILE;

    @Configuration
    static class MarkerConfig {
        @Bean
        @Profile(PROFILE)
        String marker() {
            return "present";
        }
    }

    @Test
    void theArgAloneActivatesTheProfile() {
        try (var context =
                new SpringApplicationBuilder(MarkerConfig.class)
                        .web(WebApplicationType.NONE)
                        .run(ACTIVATE_PROFILE_ARG)) {
            assertTrue(context.containsBean("marker"));
            assertTrue(List.of(context.getEnvironment().getActiveProfiles()).contains(PROFILE));
        }
    }

    @Test
    void theListenerExcludesTheProfileEvenThoughItWasActivatedAtHighPrecedence() {
        try (var context =
                new SpringApplicationBuilder(MarkerConfig.class)
                        .web(WebApplicationType.NONE)
                        .listeners(ProfileExclusion.excluding(PROFILE))
                        .run(ACTIVATE_PROFILE_ARG)) {
            assertFalse(context.containsBean("marker"));
            assertFalse(List.of(context.getEnvironment().getActiveProfiles()).contains(PROFILE));
        }
    }
}
