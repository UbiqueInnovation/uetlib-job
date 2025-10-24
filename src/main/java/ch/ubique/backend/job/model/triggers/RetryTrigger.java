package ch.ubique.backend.job.model.triggers;

import java.time.Duration;

public record RetryTrigger(int maxRetries, Duration constantBackoff) implements TriggerDefinition {}
