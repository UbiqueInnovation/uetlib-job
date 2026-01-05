package ch.ubique.backend.job.model;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.core.jackson.ObjectMapperUtils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.stream.Stream;

class JobRunArgumentsTest {

    private final ObjectMapper objectMapper = ObjectMapperUtils.objectMapper();

    @ParameterizedTest
    @MethodSource("testData")
    void additionalData(final Object additionalData) throws JacksonException {
        final var jobRunArguments = new JobRunArguments(false, additionalData);
        final var encoded = objectMapper.writeValueAsString(jobRunArguments);
        final var decoded = objectMapper.readValue(encoded, JobRunArguments.class);
        assertEquals(additionalData, decoded.additionalData());
    }

    public static Stream<Object> testData() {
        return Stream.of("1", 1, 1L, 1.0, 1.0F, new ReplayContext(1), new TestRecord("abc", 12));
    }
}
