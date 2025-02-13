package ch.ubique.backend.job.model.converters;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

public class ObjectWithTypeSerializer extends JsonSerializer<Object> {

    @Override
    public void serialize(
            final Object object,
            final JsonGenerator jsonGenerator,
            final SerializerProvider serializerProvider)
            throws IOException {
        if (object instanceof final ObjectWithType objectWithType) {
            jsonGenerator.writeObject(objectWithType);
            return;
        }
        jsonGenerator.writeObject(new ObjectWithType(object));
    }
}
