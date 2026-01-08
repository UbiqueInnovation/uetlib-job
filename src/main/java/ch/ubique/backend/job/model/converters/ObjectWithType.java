package ch.ubique.backend.job.model.converters;

import ch.ubique.backend.core.jackson.ObjectMapperUtils;

import tools.jackson.databind.json.JsonMapper;

public record ObjectWithType(Object object, String className) {

    private static final JsonMapper OBJECT_MAPPER = ObjectMapperUtils.jsonMapper();

    public ObjectWithType(final Object object) {
        this(object, object.getClass().getCanonicalName());
    }

    public static ObjectWithType from(final Object object) {
        if (object instanceof final ObjectWithType objectWithType) {
            return objectWithType;
        }
        try {
            return OBJECT_MAPPER.convertValue(object, ObjectWithType.class);
        } catch (final Exception e) {
            return new ObjectWithType(object);
        }
    }

    public Object typedObject() {
        try {
            final var type = Class.forName(className);
            return OBJECT_MAPPER.convertValue(object, type);
        } catch (final Exception e) {
            return object;
        }
    }
}
