package ch.ubique.uetlib.job.model.converters;

import ch.ubique.uetlib.core.hibernate.converter.ObjectMapperToStringConverter;
import ch.ubique.uetlib.job.model.JobRunDatabaseSubmissionArguments;

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
