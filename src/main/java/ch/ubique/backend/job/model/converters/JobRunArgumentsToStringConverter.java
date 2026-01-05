package ch.ubique.backend.job.model.converters;

import ch.ubique.backend.core.hibernate.converter.ObjectMapperToStringConverter;
import ch.ubique.backend.job.model.JobRunArguments;

import jakarta.persistence.Converter;

import tools.jackson.core.type.TypeReference;

@Converter(autoApply = true)
public class JobRunArgumentsToStringConverter
        extends ObjectMapperToStringConverter<JobRunArguments> {
    public JobRunArgumentsToStringConverter() {
        super(new TypeReference<>() {});
    }
}
