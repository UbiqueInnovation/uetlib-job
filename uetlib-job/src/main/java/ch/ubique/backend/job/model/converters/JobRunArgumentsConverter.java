package ch.ubique.backend.job.model.converters;

import ch.ubique.backend.job.model.JobRunArguments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class JobRunArgumentsConverter implements AttributeConverter<JobRunArguments, String> {
    private static final Logger logger = LoggerFactory.getLogger(JobRunArgumentsConverter.class);
    private final ObjectMapper objectMapper;

    public JobRunArgumentsConverter(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convertToDatabaseColumn(final JobRunArguments attributes) {
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (final JsonProcessingException e) {
            logger.error("Failed to serialize JobRunArguments", e);
            return null;
        }
    }

    @Override
    public JobRunArguments convertToEntityAttribute(final String dbData) {
        try {
            return objectMapper.readValue(dbData, JobRunArguments.class);
        } catch (final IOException e) {
            logger.error("Failed to deserialize JobRunArguments", e);
            return null;
        }
    }
}
