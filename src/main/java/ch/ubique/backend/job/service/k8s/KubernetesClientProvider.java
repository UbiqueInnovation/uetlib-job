package ch.ubique.backend.job.service.k8s;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class KubernetesClientProvider {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesClientProvider.class);

    final boolean k8sDisabled;

    public KubernetesClientProvider(final Environment environment) {
        this.k8sDisabled = environment.matchesProfiles("disable-k8s");
    }

    public ApiClient getDefaultApiClient() {
        if (k8sDisabled) {
            logger.info("k8s disabled via profile");
            return null;
        }

        try {
            return Config.defaultClient();
        } catch (final IOException e) {
            logger.warn("Could not connect to k8s", e);
            return null;
        }
    }
}
