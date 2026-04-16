package ch.ubique.backend.job.model.converters;

import ch.ubique.backend.core.hibernate.converter.ObjectMapperToStringConverter;
import ch.ubique.backend.job.model.JobRunDatabaseSubmissionArguments;

import jakarta.persistence.Converter;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Converter(autoApply = true)
public class JobRunDatabaseSubmissionArgumentsToStringConverter
        extends ObjectMapperToStringConverter<JobRunDatabaseSubmissionArguments> {

    public JobRunDatabaseSubmissionArgumentsToStringConverter(final JsonMapper jsonMapper) {
        super(jsonMapper, new TypeReference<>() {});
    }
}
