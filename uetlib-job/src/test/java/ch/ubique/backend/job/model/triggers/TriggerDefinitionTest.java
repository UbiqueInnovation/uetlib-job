package ch.ubique.backend.job.model.triggers;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.reflections.Reflections;

class TriggerDefinitionTest {

    @Test
    void allDefinitionsMustHaveAnEnumType() {
        final var reflections = new Reflections("ch.ubique.backend");
        final var definitionClasses = reflections.getSubTypesOf(TriggerDefinition.class);
        assertFalse(definitionClasses.isEmpty());
        for (final var defintionClass : definitionClasses) {
            assertDoesNotThrow(() -> TriggerType.getTypeForClass(defintionClass));
        }
    }
}
