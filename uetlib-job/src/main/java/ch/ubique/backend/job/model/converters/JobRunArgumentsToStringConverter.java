package ch.ubique.backend.job.model.converters;

import ch.ubique.backend.core.jackson.ObjectMapperUtils;
import ch.ubique.backend.job.model.JobRunArguments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pivovarit.function.exception.WrappedException;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;

@Converter(autoApply = true)
public class JobRunArgumentsToStringConverter
        implements AttributeConverter<JobRunArguments, String> {
    private final ObjectMapper objectMapper = ObjectMapperUtils.objectMapper();

    @Override
    public String convertToDatabaseColumn(final JobRunArguments attributes) {
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (final JsonProcessingException e) {
            throw new WrappedException(e);
        }
    }

    @Override
    public JobRunArguments convertToEntityAttribute(final String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, JobRunArguments.class);
        } catch (final IOException e) {
            throw new WrappedException(e);
        }
    }
}
