package ch.ubique.backend.job.model.converters;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class ObjectWithTypeSerializer extends ValueSerializer<Object> {

    @Override
    public void serialize(
            final Object object,
            final JsonGenerator jsonGenerator,
            final SerializationContext serializerProvider) {
        if (object instanceof final ObjectWithType objectWithType) {
            jsonGenerator.writePOJO(objectWithType);
            return;
        }

        jsonGenerator.writePOJO(new ObjectWithType(object));
    }
}
