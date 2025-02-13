package ch.ubique.backend.job.model.converters;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class ObjectWithTypeDeserializer extends JsonDeserializer<Object> {

    @Override
    public Object deserialize(final JsonParser jsonParser, final DeserializationContext context)
            throws IOException {
        final var object = jsonParser.readValueAs(Object.class);
        return ObjectWithType.from(object).typedObject();
    }
}
