package ch.uetlib.job.service.k8s;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;

@Service
@Conditional(KubernetesAvailableConfigurationCondition.class)
public class KubernetesClientProvider {

    public ApiClient getDefaultApiClient() {
        try {
            return Config.defaultClient();
        } catch (final IOException e) {
            // Should not land here, since KubernetesAvailableConfigurationCondition already tries
            // to get the client. So this bean only gets invoked if the client is available.
            throw new UncheckedIOException("Could not connect to k8s", e);
        }
    }
}
