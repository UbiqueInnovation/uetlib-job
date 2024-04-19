package ch.ubique.backend.job.model.k8s;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.kubernetes.client.openapi.models.V1Toleration;

/**
 * Use the {@link #builder()} to set only some of the fields, as none of them are actually required,
 * but some have implicit default values in k8s.
 *
 * <p>{@link #operator()} {@link KubernetesPodTolerationOperator#Equal} requires a {@link #value()},
 * while {@link KubernetesPodTolerationOperator#Exists} requires none.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record KubernetesPodToleration(
        String key,
        KubernetesPodTolerationOperator operator,
        String value,
        KubernetesPodTolerationEffect effect,
        Long tolerationSeconds) {

    public static KubernetesPodTolerationBuilder builder() {
        return new KubernetesPodTolerationBuilder();
    }

    public V1Toleration toV1Toleration() {
        return new V1Toleration()
                .key(key)
                .operator(operator.name())
                .value(value)
                .effect(effect.name())
                .tolerationSeconds(tolerationSeconds);
    }

    public static final class KubernetesPodTolerationBuilder {
        private String key;
        private KubernetesPodTolerationOperator operator;
        private String value;
        private KubernetesPodTolerationEffect effect;
        private Long tolerationSeconds;

        private KubernetesPodTolerationBuilder() {}

        public KubernetesPodTolerationBuilder withKey(final String key) {
            this.key = key;
            return this;
        }

        public KubernetesPodTolerationBuilder withOperator(
                final KubernetesPodTolerationOperator operator) {
            this.operator = operator;
            return this;
        }

        public KubernetesPodTolerationBuilder withValue(final String value) {
            this.value = value;
            return this;
        }

        public KubernetesPodTolerationBuilder withEffect(
                final KubernetesPodTolerationEffect effect) {
            this.effect = effect;
            return this;
        }

        public KubernetesPodTolerationBuilder withTolerationSeconds(final Long tolerationSeconds) {
            this.tolerationSeconds = tolerationSeconds;
            return this;
        }

        public KubernetesPodToleration build() {
            return new KubernetesPodToleration(key, operator, value, effect, tolerationSeconds);
        }
    }
}
