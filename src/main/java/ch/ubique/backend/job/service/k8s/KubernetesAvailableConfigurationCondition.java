package ch.ubique.backend.job.service.k8s;

import io.kubernetes.client.util.Config;
import java.io.IOException;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class KubernetesAvailableConfigurationCondition implements ConfigurationCondition {

    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.PARSE_CONFIGURATION;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (context.getEnvironment().matchesProfiles("disable-k8s")) {
            return false;
        }

        try {
            Config.defaultClient();
            return true;
        } catch (final IOException | IllegalArgumentException e) {
            return false;
        }
    }
}
