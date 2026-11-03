package ch.ubique.uetlib.job.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.ubique.uetlib.job.model.FileTriggerContext;
import ch.ubique.uetlib.job.model.JobRunArguments;
import ch.ubique.uetlib.job.model.RetryTriggerContext;
import ch.ubique.uetlib.job.model.converters.JobRunArgumentsToStringConverter;
import ch.ubique.uetlib.job.model.regex.SerializableMatchResult;
import ch.ubique.uetlib.job.model.triggers.FileChangeEvent;
import ch.ubique.uetlib.job.model.triggers.TriggerType;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.regex.Pattern;

class JobRunArgumentsToStringConverterTest {

    private static final Pattern TEST_PATTERN =
            Pattern.compile("\\w+-(?<blockB>\\w+)-(?<blockC>\\w+)-\\w+.txt");

    private static final JsonMapper JSON_MAPPER =
            JsonMapper.builder()
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .build();

    private final JobRunArgumentsToStringConverter jobRunArgumentsToStringConverter =
            new JobRunArgumentsToStringConverter(JSON_MAPPER);

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
                "{\"forceRun\":true,\"triggerContext\":{\"type\":\"RETRY\",\"retryCount\": 1,\"maxRetries\": 3,\"originalJobRunId\": 15,\"JOHN\":\"CENA\"},\"unknownProperty\":\"how"
                        + " did I get here\"}";
        final var jobRunArguments =
                jobRunArgumentsToStringConverter.convertToEntityAttribute(dbData);
        assertTrue(jobRunArguments.forceRun());
        assertEquals(new RetryTriggerContext(1, 3, 15), jobRunArguments.triggerContext());
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
    void testConvertFileTriggerContextToDatabaseColumn() {
        final var testFileName = "AAAA-BBBB-CCCC-DDDD.txt";
        final var matcher = TEST_PATTERN.matcher(testFileName);
        assertTrue(matcher.matches());
        final var serializableMatchResult = SerializableMatchResult.fromMatcher(matcher);
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
                JSON_MAPPER.readValue(expected, Object.class),
                JSON_MAPPER.readValue(dbData, Object.class));
    }

    @Test
    void testConvertFileTriggerContextFromDatabaseColumn() {
        final var dbData =
                "{\"forceRun\":true,\"triggerContext\":{\"fileChangeEvent\":\"ENTRY_CREATED\",\"fileName\":\"AAAA-BBBB-CCCC-DDDD.txt\",\"matchResult\":{\"first\":0,\"groupCount\":2,\"groupIndices\":[0,23,5,9,10,14],\"groups\":[\"AAAA-BBBB-CCCC-DDDD.txt\",\"BBBB\",\"CCCC\"],\"last\":23,\"namedGroups\":{\"blockB\":1,\"blockC\":2},\"text\":\"AAAA-BBBB-CCCC-DDDD.txt\"},\"watchedPath\":\"file:///tmp/\",\"type\":\"FILE\"}}";
        final var testFileName = "AAAA-BBBB-CCCC-DDDD.txt";
        final var matcher = TEST_PATTERN.matcher(testFileName);
        assertTrue(matcher.matches());
        final var serializableMatchResult = SerializableMatchResult.fromMatcher(matcher);
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
