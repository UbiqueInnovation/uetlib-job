package ch.ubique.backend.job.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.ubique.backend.core.jackson.ObjectMapperUtils;
import ch.ubique.backend.core.utils.RegexUtil;
import ch.ubique.backend.job.model.FileTriggerContext;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.RetryTriggerContext;
import ch.ubique.backend.job.model.converters.JobRunArgumentsToStringConverter;
import ch.ubique.backend.job.model.triggers.FileChangeEvent;
import ch.ubique.backend.job.model.triggers.TriggerType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.regex.Pattern;

class JobRunArgumentsToStringConverterTest {

    private static final Pattern TEST_PATTERN =
            Pattern.compile("\\w+-(?<blockB>\\w+)-(?<blockC>\\w+)-\\w+.txt");

    private final ObjectMapper objectMapperOrdered =
            ObjectMapperUtils.objectMapper(
                    customizer ->
                            customizer
                                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS));
    private final JobRunArgumentsToStringConverter jobRunArgumentsToStringConverter =
            new JobRunArgumentsToStringConverter();

    @BeforeEach
    void beforeEach() {
        ReflectionTestUtils.setField(
                jobRunArgumentsToStringConverter, "objectMapper", objectMapperOrdered);
    }

    @Test
    void testConvertForceRunToDatabaseColumn() {
        final var jobRunArguments = new JobRunArguments(true);
        final var dbData =
                jobRunArgumentsToStringConverter.convertToDatabaseColumn(jobRunArguments);
        assertEquals("{\"forceRun\":true}", dbData);
    }

    @Test
    void testConvertForceRunFromDatabaseColumn() {
        final var dbData =
                "{\"forceRun\":true,\"triggerContext\":{\"type\":\"RETRY\",\"JOHN\":\"CENA\"},\"unknownProperty\":\"how"
                        + " did I get here\"}";
        final var jobRunArguments =
                jobRunArgumentsToStringConverter.convertToEntityAttribute(dbData);
        assertTrue(jobRunArguments.forceRun());
        assertEquals(new RetryTriggerContext(0, 0, 0), jobRunArguments.triggerContext());
    }

    @Test
    void testConvertUnknownType() {
        final var dbData = "{\"forceRun\":true,\"triggerContext\":{\"type\":\"EMPTY\"}}";
        final var jobRunArguments =
                jobRunArgumentsToStringConverter.convertToEntityAttribute(dbData);
        assertTrue(jobRunArguments.forceRun());
        assertNull(jobRunArguments.triggerContext());
    }

    @Test
    void testConvertFileTriggerContextToDatabaseColumn() throws JsonProcessingException {
        final var testFileName = "AAAA-BBBB-CCCC-DDDD.txt";
        final var matcher = TEST_PATTERN.matcher(testFileName);
        assertTrue(matcher.matches());
        final var serializableMatchResult = RegexUtil.toSerializableMatchResult(matcher);
        final var jobRunArguments =
                new JobRunArguments(
                        true,
                        TriggerType.FILE,
                        new FileTriggerContext(
                                Path.of("/tmp"),
                                FileChangeEvent.ENTRY_CREATED,
                                testFileName,
                                serializableMatchResult),
                        null,
                        null);
        final var dbData =
                jobRunArgumentsToStringConverter.convertToDatabaseColumn(jobRunArguments);
        final var expected =
                "{\"forceRun\":true,\"triggerContext\":{\"fileChangeEvent\":\"ENTRY_CREATED\",\"fileName\":\"AAAA-BBBB-CCCC-DDDD.txt\",\"matchResult\":{\"first\":0,\"groupCount\":2,\"groupIndices\":[0,23,5,9,10,14],\"groups\":[\"AAAA-BBBB-CCCC-DDDD.txt\",\"BBBB\",\"CCCC\"],\"last\":23,\"namedGroups\":{\"blockB\":1,\"blockC\":2},\"text\":\"AAAA-BBBB-CCCC-DDDD.txt\"},\"watchedPath\":\"file:///tmp/\",\"type\":\"FILE\"},\"triggerType\":\"FILE\"}";
        assertEquals(
                objectMapperOrdered.readValue(expected, Object.class),
                objectMapperOrdered.readValue(dbData, Object.class));
    }

    @Test
    void testConvertFileTriggerContextFromDatabaseColumn() {
        final var dbData =
                "{\"forceRun\":true,\"triggerContext\":{\"fileChangeEvent\":\"ENTRY_CREATED\",\"fileName\":\"AAAA-BBBB-CCCC-DDDD.txt\",\"matchResult\":{\"first\":0,\"groupCount\":2,\"groupIndices\":[0,23,5,9,10,14],\"groups\":[\"AAAA-BBBB-CCCC-DDDD.txt\",\"BBBB\",\"CCCC\"],\"last\":23,\"namedGroups\":{\"blockB\":1,\"blockC\":2},\"text\":\"AAAA-BBBB-CCCC-DDDD.txt\"},\"watchedPath\":\"file:///tmp/\",\"type\":\"FILE\"}}";
        final var testFileName = "AAAA-BBBB-CCCC-DDDD.txt";
        final var matcher = TEST_PATTERN.matcher(testFileName);
        assertTrue(matcher.matches());
        final var serializableMatchResult = RegexUtil.toSerializableMatchResult(matcher);
        final var jobRunArguments =
                jobRunArgumentsToStringConverter.convertToEntityAttribute(dbData);
        assertTrue(jobRunArguments.forceRun());
        assertEquals(
                new FileTriggerContext(
                        Path.of("/tmp"),
                        FileChangeEvent.ENTRY_CREATED,
                        testFileName,
                        serializableMatchResult),
                jobRunArguments.triggerContext());
    }
}
