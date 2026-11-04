package ch.uetlib.job.model.converters;

import ch.uetlib.core.hibernate.converter.ObjectMapperToStringConverter;
import ch.uetlib.job.model.triggers.TriggerDefinition;

import jakarta.persistence.Converter;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Converter(autoApply = true)
public class TriggerDefinitionToStringConverter
        extends ObjectMapperToStringConverter<TriggerDefinition> {

    public TriggerDefinitionToStringConverter(final JsonMapper jsonMapper) {
        super(jsonMapper, new TypeReference<>() {});
    }
}
