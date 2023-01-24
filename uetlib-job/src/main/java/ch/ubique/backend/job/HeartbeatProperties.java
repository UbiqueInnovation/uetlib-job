package ch.ubique.backend.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ch.ubique.backend.job.interface.heartbeat")
public record HeartbeatProperties(boolean enabled, String endpoint) {}
