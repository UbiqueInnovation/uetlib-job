package ch.ubique.backend.job.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.ubique.backend.core.regex.SerializableMatchResult;
import ch.ubique.backend.core.utils.RegexUtil;
import ch.ubique.backend.job.model.EmptyTriggerContext;
import ch.ubique.backend.job.model.FileTriggerContext;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.converters.JobRunArgumentsToStringConverter;
import ch.ubique.backend.job.model.triggers.FileChangeEvent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.regex.Pattern;

class JobRunArgumentsToStringConverterTest {

    private static final Pattern TEST_PATTERN =
            Pattern.compile("\\w+-(?<blockB>\\w+)-(?<blockC>\\w+)-\\w+.txt");
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
                "{\"forceRun\":true,\"triggerContext\":{\"type\":\"EMPTY\",\"JOHN\":\"CENA\"},\"unknownProperty\":\"how"
                + " did I get here\"}";
        final var jobRunArguments =
                jobRunArgumentsToStringConverter.convertToEntityAttribute(dbData);
        assertTrue(jobRunArguments.forceRun());
        assertEquals(new EmptyTriggerContext(), jobRunArguments.triggerContext());
    }

    @Test
    void testConvertFileTriggerContextToDatabaseColumn() {
        final var testFileName = "AAAA-BBBB-CCCC-DDDD.txt";
        final var matcher = TEST_PATTERN.matcher(testFileName);
        //noinspection ResultOfMethodCallIgnored
        matcher.matches();
        final var serializableMatchResult =
                (SerializableMatchResult) RegexUtil.toSerializableMatchResult(matcher);
        final var jobRunArguments =
                new JobRunArguments(
                        true,
                        new FileTriggerContext(
                                Path.of("/tmp"),
                                FileChangeEvent.ENTRY_CREATED,
                                testFileName,
                                serializableMatchResult),
                        null);
        final var dbData =
                jobRunArgumentsToStringConverter.convertToDatabaseColumn(jobRunArguments);
        assertEquals(
                "{\"forceRun\":true,\"triggerContext\":{\"watchedPath\":\"file:///tmp/\",\"fileChangeEvent\":\"ENTRY_CREATED\",\"fileName\":\"AAAA-BBBB-CCCC-DDDD.txt\",\"matchResult\":{\"first\":0,\"last\":23,\"groupCount\":2,\"groups\":[\"AAAA-BBBB-CCCC-DDDD.txt\",\"BBBB\",\"CCCC\"],\"groupIndices\":[0,23,5,9,10,14],\"text\":\"AAAA-BBBB-CCCC-DDDD.txt\",\"namedGroups\":{\"blockC\":2,\"blockB\":1}},\"type\":\"FILE\"}}",
                dbData);
    }

    @Test
    void testConvertFileTriggerContextFromDatabaseColumn() {
        final var dbData =
                "{\"forceRun\":true,\"triggerContext\":{\"watchedPath\":\"file:///tmp/\",\"fileChangeEvent\":\"ENTRY_CREATED\",\"fileName\":\"AAAA-BBBB-CCCC-DDDD.txt\",\"matchResult\":{\"first\":0,\"last\":23,\"groupCount\":2,\"groups\":[\"AAAA-BBBB-CCCC-DDDD.txt\",\"BBBB\",\"CCCC\"],\"groupIndices\":[0,23,5,9,10,14],\"text\":\"AAAA-BBBB-CCCC-DDDD.txt\",\"namedGroups\":{\"blockC\":2,\"blockB\":1}},\"type\":\"FILE\"}}";
        final var testFileName = "AAAA-BBBB-CCCC-DDDD.txt";
        final var matcher = TEST_PATTERN.matcher(testFileName);
        //noinspection ResultOfMethodCallIgnored
        matcher.matches();
        final var serializableMatchResult =
                (SerializableMatchResult) RegexUtil.toSerializableMatchResult(matcher);
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
