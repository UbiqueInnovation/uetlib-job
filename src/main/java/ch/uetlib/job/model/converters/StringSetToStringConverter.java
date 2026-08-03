package ch.uetlib.job.model.converters;

import ch.uetlib.core.hibernate.converter.ObjectMapperToStringConverter;

import jakarta.persistence.Converter;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

@Converter(autoApply = true)
public class StringSetToStringConverter<T> extends ObjectMapperToStringConverter<Set<T>> {

    public StringSetToStringConverter(final JsonMapper jsonMapper) {
        super(jsonMapper, new TypeReference<>() {});
    }
}
