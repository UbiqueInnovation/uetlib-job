package ch.ubique.backend.job.model.converters;

import ch.ubique.backend.core.hibernate.converter.ObjectMapperToStringConverter;
import ch.ubique.backend.job.model.JobRunArguments;

import jakarta.persistence.Converter;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Converter(autoApply = true)
public class JobRunArgumentsToStringConverter
        extends ObjectMapperToStringConverter<JobRunArguments> {

    public JobRunArgumentsToStringConverter(final ObjectMapper objectMapper) {
        super(objectMapper, new TypeReference<>() {});
    }
}
