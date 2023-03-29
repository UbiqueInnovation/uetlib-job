package ch.ubique.backend.job.model.properties.enums;

import ch.ubique.backend.core.utils.StringEncoderDecoder;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Optional;

public enum KubernetesSku {
    UNSET(null),
    HIGH_MEMORY("highmem"),
    HIGH_PERFORMANCE("highperf");

    private final String value;

    KubernetesSku(final String value) {
        this.value = value;
    }

    public Optional<String> value() {
        return Optional.ofNullable(value);
    }

    public static final StringEncoderDecoder<KubernetesSku> STRING_ENCODER_DECODER =
            new StringEncoderDecoder<>() {
                @Override
                public KubernetesSku decode(final String value) {
                    return Arrays.stream(KubernetesSku.values())
                            .filter(
                                    sku ->
                                            StringUtils.equalsIgnoreCase(
                                                    value, sku.value().orElse(null)))
                            .findAny()
                            .orElse(KubernetesSku.UNSET);
                }

                @Override
                public String encode(final KubernetesSku sku) {
                    return sku.value().orElse(null);
                }
            };
}
