package ch.ubique.backend.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ch.ubique.backend.job")
public record JobProperties(String checkEndpoint) {}
