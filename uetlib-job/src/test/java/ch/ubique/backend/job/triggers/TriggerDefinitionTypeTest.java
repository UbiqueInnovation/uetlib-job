package ch.ubique.backend.job.triggers;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.job.model.triggers.CronTriggerDefinition;
import ch.ubique.backend.job.model.triggers.JobStateTriggerDefinition;
import ch.ubique.backend.job.model.triggers.StartupTriggerDefinition;
import ch.ubique.backend.job.model.triggers.TriggerType;

import org.junit.jupiter.api.Test;

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TriggerDefinitionTypeTest {

    @Test
    void testTypesMatch() {
        final var definitions =
                Stream.of(
                                new StartupTriggerDefinition(),
                                new CronTriggerDefinition(null),
                                new JobStateTriggerDefinition(null, null))
                        .collect(Collectors.toMap(Object::getClass, Function.identity()));

        for (final var type : TriggerType.values()) {
            assertEquals(type, definitions.get(TriggerType.getClassForType(type)).type());
        }
    }
}
