package ch.ubique.backend.job.service.k8s;

import io.kubernetes.client.util.Config;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.io.IOException;

@NullMarked
public class KubernetesAvailableConfigurationCondition implements ConfigurationCondition {

    public static final String DISABLE_K8S_PROFILE = "disable-k8s";

    private static final Logger logger =
            LoggerFactory.getLogger(KubernetesAvailableConfigurationCondition.class);

    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.PARSE_CONFIGURATION;
    }

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
        if (context.getEnvironment().matchesProfiles(DISABLE_K8S_PROFILE)) {
            logger.info("Disabled k8s with profile {}", DISABLE_K8S_PROFILE);
            return false;
        }

        try {
            Config.defaultClient();
            return true;
        } catch (final IOException | IllegalArgumentException e) {
            logger.warn("Could not connect to k8s", e);
            return false;
        }
    }
}
