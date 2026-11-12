package ch.uetlib.job;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

import java.util.Arrays;

/**
 * Forces a Spring profile inactive even when it was activated from external config ({@code
 * spring.profiles.active}, a config map, ...) rather than in code.
 */
/*
 * {@code SpringApplicationBuilder.profiles(...)} can only <em>add</em> profiles; it cannot
 * remove one that config already turned on. {@link #excluding} returns an {@link ApplicationListener}
 * that runs on {@link ApplicationEnvironmentPreparedEvent} - after the environment (and its
 * profiles) is resolved but before the context is refreshed - and rewrites the active-profile list
 * without the unwanted entry. Used so a dedicated {@code --job} process never inherits
 * {@code job-primary-triggers} (see {@code JobApplication}).
 */
public final class ProfileExclusion {

    private ProfileExclusion() {}

    public static ApplicationListener<ApplicationEnvironmentPreparedEvent> excluding(
            final String profile) {
        return event -> {
            final var environment = event.getEnvironment();
            environment.setActiveProfiles(withoutProfile(environment.getActiveProfiles(), profile));
        };
    }

    static String[] withoutProfile(final String[] activeProfiles, final String toExclude) {
        return Arrays.stream(activeProfiles)
                .filter(profile -> !toExclude.equals(profile))
                .toArray(String[]::new);
    }
}
