package ch.ubique.backend.job.model.converters;

import ch.ubique.backend.core.hibernate.converter.ObjectMapperToStringConverter;
import ch.ubique.backend.job.model.JobRunArguments;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class JobRunArgumentsToStringConverter
        extends ObjectMapperToStringConverter<JobRunArguments> {
    public JobRunArgumentsToStringConverter() {
        super(new TypeReference<>() {});
    }
}
