package ch.uetlib.job;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

import java.util.Arrays;

/** Forces a profile inactive even if it was resolved from config, not set programmatically. */
public final class ProfileExclusion {

    private ProfileExclusion() {}

    public static ApplicationListener<ApplicationEnvironmentPreparedEvent> excluding(
            final String profile) {
        return event -> {
            final var environment = event.getEnvironment();
            environment.setActiveProfiles(
                    withoutProfile(environment.getActiveProfiles(), profile));
        };
    }

    static String[] withoutProfile(final String[] activeProfiles, final String toExclude) {
        return Arrays.stream(activeProfiles)
                .filter(profile -> !toExclude.equals(profile))
                .toArray(String[]::new);
    }
}
