package ch.ubique.backend.job.model.converters;

import ch.ubique.backend.core.hibernate.converter.ObjectMapperToStringConverter;
import ch.ubique.backend.job.model.triggers.TriggerDefinition;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TriggerDefinitionToStringConverter
        extends ObjectMapperToStringConverter<TriggerDefinition> {

    public TriggerDefinitionToStringConverter() {
        super(new TypeReference<>() {});
    }
}
