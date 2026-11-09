package ch.uetlib.job.model.triggers;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonSubTypes;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;

import java.util.Arrays;
import java.util.stream.Collectors;

class TriggerDefinitionTest {

    @Test
    void allDefinitionsMustHaveAnEnumType() {
        final var reflections = new Reflections("ch.uetlib");
        final var definitionClasses = reflections.getSubTypesOf(TriggerDefinition.class);
        assertFalse(definitionClasses.isEmpty());
        for (final var definitionClass : definitionClasses) {
            assertDoesNotThrow(() -> TriggerType.getTypeForClass(definitionClass));
        }
    }

    @Test
    void allDefinitionsAsJsonSubTypes() {
        final var jsonSubTypesAnnotation =
                TriggerDefinition.class.getAnnotation(JsonSubTypes.class);
        assertNotNull(jsonSubTypesAnnotation);

        final var triggerTypes =
                Arrays.stream(TriggerType.values())
                        .map(
                                triggerType ->
                                        Pair.of(triggerType.name(), triggerType.definitionClass()))
                        .collect(Collectors.toSet());
        final var subTypes =
                Arrays.stream(jsonSubTypesAnnotation.value())
                        .map(subType -> Pair.of(subType.name(), subType.value()))
                        .collect(Collectors.toSet());
        assertEquals(triggerTypes, subTypes);
    }
}
