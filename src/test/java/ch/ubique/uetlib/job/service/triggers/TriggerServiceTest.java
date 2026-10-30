package ch.ubique.uetlib.job.service.triggers;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.uetlib.job.model.triggers.TriggerDefinition;
import ch.ubique.uetlib.job.model.triggers.TriggerType;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;
import org.springframework.core.GenericTypeResolver;

class TriggerServiceTest {
    @Test
    void allTriggerTypesMustHaveOneService() {
        final var reflections = new Reflections("ch.ubique.uetlib.job");
        final var triggerServices = reflections.getSubTypesOf(TriggerService.class);
        assertFalse(triggerServices.isEmpty(), "Could not find any services.");

        //noinspection unchecked,DataFlowIssue
        final var servicesForType =
                triggerServices.stream()
                        .collect(
                                Collectors.groupingBy(
                                        cls ->
                                                (Class<? extends TriggerDefinition>)
                                                        GenericTypeResolver.resolveTypeArgument(
                                                                cls, TriggerService.class)));

        for (final var triggerType : TriggerType.values()) {
            final var definitionClass = triggerType.definitionClass();
            assertEquals(
                    1,
                    Optional.ofNullable(servicesForType.get(definitionClass))
                            .map(List::size)
                            .orElse(0),
                    "Expected exactly one "
                            + TriggerService.class.getSimpleName()
                            + " for "
                            + TriggerType.class.getSimpleName()
                            + "."
                            + triggerType);
        }
    }
}
