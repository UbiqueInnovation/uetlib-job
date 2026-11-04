package ch.uetlib.job.model.properties;

import org.apache.commons.lang3.BooleanUtils;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

final class BooleanPropertyDefinition extends AbstractJobPropertyDefinition<Boolean> {

    BooleanPropertyDefinition(
            final String key,
            final String description,
            final JobPropertyDefinitionGroup group,
            final boolean defaultValue) {
        super(key, description, group, false, defaultValue, Set.of(true, false));
    }

    @Override
    public Boolean decodeFromString(final @Nullable String value) {
        return BooleanUtils.toBoolean(value);
    }

    @Override
    public String encodeFromString(final Boolean value) {
        return String.valueOf(Objects.requireNonNull(value));
    }
}
