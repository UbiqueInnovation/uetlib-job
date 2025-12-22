package ch.ubique.backend.job.service.k8s;

import io.kubernetes.client.util.Config;

import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.io.IOException;

public class KubernetesAvailableConfigurationCondition implements ConfigurationCondition {
    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.PARSE_CONFIGURATION;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        try {
            Config.defaultClient();
            return true;
        } catch (final IOException | IllegalArgumentException e) {
            return false;
        }
    }
}
