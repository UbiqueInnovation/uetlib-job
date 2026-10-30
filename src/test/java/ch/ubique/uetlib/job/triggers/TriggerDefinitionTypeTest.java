package ch.ubique.uetlib.job.triggers;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.uetlib.job.model.triggers.TriggerType;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TriggerDefinitionTypeTest {

    @Test
    void testNoDuplicates() {
        assertEquals(
                TriggerType.values().length,
                Arrays.stream(TriggerType.values())
                        .map(TriggerType::definitionClass)
                        .distinct()
                        .count());
    }
}
