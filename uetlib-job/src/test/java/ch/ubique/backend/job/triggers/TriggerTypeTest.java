package ch.ubique.backend.job.triggers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import ch.ubique.backend.job.model.triggers.TriggerType;
import org.junit.jupiter.api.Test;

class TriggerTypeTest {
    
    @Test
    void testTypesMatch() {
        for (final var type : TriggerType.values()) {
            final var instance = mock(TriggerType.getClassForType(type));
            assertEquals(type, instance.type());
        }
    }
}
