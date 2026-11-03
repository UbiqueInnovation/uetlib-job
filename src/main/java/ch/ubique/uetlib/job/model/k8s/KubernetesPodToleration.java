package ch.ubique.uetlib.job.model.k8s;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.kubernetes.client.openapi.models.V1Toleration;

import org.jspecify.annotations.Nullable;

/**
 * Use the {@link #builder()} to set only some of the fields, as none of them are actually required,
 * but some have implicit default values in k8s.
 *
 * <p>{@link #operator()} {@link KubernetesPodTolerationOperator#Equal} requires a {@link #value()},
 * while {@link KubernetesPodTolerationOperator#Exists} requires none. {@link
 * KubernetesPodTolerationOperator#Equal} is also the default operator in k8s if unset.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record KubernetesPodToleration(
        @Nullable String key,
        @Nullable KubernetesPodTolerationOperator operator,
        @Nullable String value,
        @Nullable KubernetesPodTolerationEffect effect,
        @Nullable Long tolerationSeconds) {

    public static KubernetesPodTolerationBuilder builder() {
        return new KubernetesPodTolerationBuilder();
    }

    public V1Toleration toV1Toleration() {
        return new V1Toleration()
                .key(key)
                .operator(operator != null ? operator.name() : null)
                .value(value)
                .effect(effect != null ? effect.name() : null)
                .tolerationSeconds(tolerationSeconds);
    }

    public static final class KubernetesPodTolerationBuilder {
        private @Nullable String key;
        private @Nullable KubernetesPodTolerationOperator operator;
        private @Nullable String value;
        private @Nullable KubernetesPodTolerationEffect effect;
        private @Nullable Long tolerationSeconds;

        private KubernetesPodTolerationBuilder() {}

        public KubernetesPodTolerationBuilder withKey(final @Nullable String key) {
            this.key = key;
            return this;
        }

        public KubernetesPodTolerationBuilder withOperator(
                final @Nullable KubernetesPodTolerationOperator operator) {
            this.operator = operator;
            return this;
        }

        public KubernetesPodTolerationBuilder withValue(final @Nullable String value) {
            this.value = value;
            return this;
        }

        public KubernetesPodTolerationBuilder withEffect(
                final @Nullable KubernetesPodTolerationEffect effect) {
            this.effect = effect;
            return this;
        }

        public KubernetesPodTolerationBuilder withTolerationSeconds(
                final @Nullable Long tolerationSeconds) {
            this.tolerationSeconds = tolerationSeconds;
            return this;
        }

        public KubernetesPodToleration build() {
            return new KubernetesPodToleration(key, operator, value, effect, tolerationSeconds);
        }
    }
}
