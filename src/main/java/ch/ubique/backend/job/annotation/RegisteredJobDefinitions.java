package ch.ubique.backend.job.annotation;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * Programmatically registered as a bean in {@link RegisteredJobRegistrar} with all discovered
 * {@link #registeredJobClasses}.
 */
public record RegisteredJobDefinitions(@NotNull Set<Class<?>> registeredJobClasses) {}
