package ch.ubique.backend.job.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.converters.JobRunArgumentsToStringConverter;

import org.junit.jupiter.api.Test;

class JobRunArgumentsToStringConverterTest {
    private final JobRunArgumentsToStringConverter jobRunArgumentsToStringConverter = new JobRunArgumentsToStringConverter();
    @Test
    void testConvertToDatabaseColumn() {
        final var jobRunArguments = new JobRunArguments(true);
        final var dbData = jobRunArgumentsToStringConverter.convertToDatabaseColumn(jobRunArguments);
        assertEquals(
                "{\"forceRun\":true,\"triggerContext\":{\"type\":\"ch.ubique.backend.job.model.EmptyTriggerContext\"},\"additionalData\":null}",
                dbData);
    }
}
