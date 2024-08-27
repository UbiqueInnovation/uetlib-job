package ch.ubique.backend.job.definitions;

import ch.ubique.backend.job.annotation.RegisteredJobRegistrar;

import java.util.Set;

/**
 * Programmatically registered as a bean in {@link RegisteredJobRegistrar} with all discovered
 * {@link #registeredJobClasses}.
 */
public record RegisteredJobDefinitions(Set<Class<?>> registeredJobClasses) {}
