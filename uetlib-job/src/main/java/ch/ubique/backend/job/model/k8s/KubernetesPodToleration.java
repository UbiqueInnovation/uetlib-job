package ch.ubique.backend.job.model.k8s;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.kubernetes.client.openapi.models.V1Toleration;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record KubernetesPodToleration(
        String key, String operator, String value, String effect, Long tolerationSeconds) {

    public V1Toleration toV1Toleration() {
        return new V1Toleration()
                .key(key)
                .operator(operator())
                .value(value)
                .effect(effect)
                .tolerationSeconds(tolerationSeconds);
    }
}
