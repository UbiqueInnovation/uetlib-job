package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.core.jackson.ObjectMapperUtils;
import ch.ubique.backend.job.model.triggers.TriggerDefinition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pivovarit.function.exception.WrappedException;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TriggerDefinitionToStringConverter
        implements AttributeConverter<TriggerDefinition, String> {

    private final ObjectMapper objectMapper = ObjectMapperUtils.objectMapper();

    @Override
    public String convertToDatabaseColumn(final TriggerDefinition attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (final JsonProcessingException e) {
            throw new WrappedException(e);
        }
    }

    @Override
    public TriggerDefinition convertToEntityAttribute(final String dbData) {
        try {
            return objectMapper.readValue(dbData, TriggerDefinition.class);
        } catch (final JsonProcessingException e) {
            throw new WrappedException(e);
        }
    }
}
