package ch.uetlib.job.model.converters;

import ch.uetlib.core.hibernate.converter.ObjectMapperToStringConverter;
import ch.uetlib.job.model.JobRunArguments;

import jakarta.persistence.Converter;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Converter(autoApply = true)
public class JobRunArgumentsToStringConverter
        extends ObjectMapperToStringConverter<JobRunArguments> {

    public JobRunArgumentsToStringConverter(final JsonMapper jsonMapper) {
        super(jsonMapper, new TypeReference<>() {});
    }
}
