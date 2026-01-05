package ch.ubique.backend.job.model.converters;

import ch.ubique.backend.core.hibernate.converter.ObjectMapperToStringConverter;
import ch.ubique.backend.job.model.JobRunDatabaseSubmissionArguments;

import tools.jackson.core.type.TypeReference;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class JobRunDatabaseSubmissionArgumentsToStringConverter
        extends ObjectMapperToStringConverter<JobRunDatabaseSubmissionArguments> {
    public JobRunDatabaseSubmissionArgumentsToStringConverter() {
        super(new TypeReference<>() {});
    }
}
