package ch.ubique.backend.job.model.converters;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class ObjectWithTypeDeserializer extends ValueDeserializer<Object> {

    @Override
    public Object deserialize(final JsonParser jsonParser, final DeserializationContext context) {
        final var object = jsonParser.readValueAs(Object.class);
        return ObjectWithType.from(object).typedObject();
    }
}
