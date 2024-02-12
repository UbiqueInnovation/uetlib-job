package ch.ubique.backend.job.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.ubique.backend.job.model.EmptyTriggerContext;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.converters.JobRunArgumentsToStringConverter;

import org.junit.jupiter.api.Test;

class JobRunArgumentsToStringConverterTest {
    private final JobRunArgumentsToStringConverter jobRunArgumentsToStringConverter =
            new JobRunArgumentsToStringConverter();

    @Test
    void testConvertForceRunToDatabaseColumn() {
        final var jobRunArguments = new JobRunArguments(true);
        final var dbData =
                jobRunArgumentsToStringConverter.convertToDatabaseColumn(jobRunArguments);
        assertEquals("{\"forceRun\":true,\"triggerContext\":{\"type\":\"EMPTY\"}}", dbData);
    }

    @Test
    void testConvertForceRunFromDatabaseColumn() {
        final var dbData =
                "{\"forceRun\":true,\"triggerContext\":{\"type\":\"EMPTY\",\"JOHN\":\"CENA\"},\"unknownProperty\":\"how did I get here\"}";
        final var jobRunArguments =
                jobRunArgumentsToStringConverter.convertToEntityAttribute(dbData);
        assertTrue(jobRunArguments.forceRun());
        assertEquals(new EmptyTriggerContext(), jobRunArguments.triggerContext());
    }
}
