package ch.ubique.backend.job.model.converters;

import ch.ubique.backend.core.hibernate.converter.ObjectMapperToStringConverter;

import jakarta.persistence.Converter;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

@Converter
public class StringSetToStringConverter extends ObjectMapperToStringConverter<Set<String>> {

    public StringSetToStringConverter(final JsonMapper jsonMapper) {
        super(jsonMapper, new TypeReference<>() {});
    }
}
