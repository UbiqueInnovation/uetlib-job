package ch.ubique.backend.job.model.regex;

import static ch.ubique.backend.test.assertion.AssertionUtils.assertThrowsWithMessage;

import static org.junit.jupiter.api.Assertions.*;

import static java.util.Map.*;
import static java.util.stream.Collectors.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SerializableMatchResultTest {

    private static final Pattern TEST_PATTERN =
            Pattern.compile("\\w+-(?<blockB>\\w+)-(?<blockC>\\w+)-\\w+");
    private static Matcher matcher;
    private static MatchResult matchResult;
    private static SerializableMatchResult serializableMatchResult;

    private final ObjectMapper objectMapper = JsonMapper.shared();

    @BeforeAll
    static void beforeAll() {
        matcher = TEST_PATTERN.matcher("AAAA-BBBB-CCCC-DDDD");
        //noinspection ResultOfMethodCallIgnored
        matcher.matches();
        matchResult = matcher.toMatchResult();
        serializableMatchResult = SerializableMatchResult.fromMatcher(matcher);
    }

    @Test
    void testSerializableMatchResultJackson() {
        final var serialized = objectMapper.writeValueAsString(serializableMatchResult);
        final var deserialized = objectMapper.readValue(serialized, SerializableMatchResult.class);
        assertEquals(serializableMatchResult, deserialized);
    }

    @Test
    void testMatcherMatches() {
        assertTrue(matcher.matches());
    }

    @Test
    void testHasMatch() {
        assertTrue(serializableMatchResult.hasMatch());
        assertEquals(matchResult.hasMatch(), serializableMatchResult.hasMatch());
    }

    @Test
    void testGroupCount() {
        assertEquals(2, serializableMatchResult.groupCount());
        assertEquals(matchResult.groupCount(), serializableMatchResult.groupCount());
    }

    @Test
    void testStart() {
        assertEquals(0, serializableMatchResult.start());
        assertEquals(matchResult.start(), serializableMatchResult.start());
        assertEquals(0, serializableMatchResult.start(0));
        assertEquals(matchResult.start(0), serializableMatchResult.start(0));
        assertEquals(5, serializableMatchResult.start(1));
        assertEquals(matchResult.start(1), serializableMatchResult.start(1));
        assertEquals(10, serializableMatchResult.start(2));
        assertEquals(matchResult.start(2), serializableMatchResult.start(2));
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class, () -> matchResult.start(3), "No group 3");
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class,
                () -> serializableMatchResult.start(3),
                "No group 3");
    }

    @Test
    void testEnd() {
        assertEquals(19, serializableMatchResult.end());
        assertEquals(matchResult.end(), serializableMatchResult.end());
        assertEquals(19, serializableMatchResult.end(0));
        assertEquals(matchResult.end(0), serializableMatchResult.end(0));
        assertEquals(9, serializableMatchResult.end(1));
        assertEquals(matchResult.end(1), serializableMatchResult.end(1));
        assertEquals(14, serializableMatchResult.end(2));
        assertEquals(matchResult.end(2), serializableMatchResult.end(2));
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class, () -> matchResult.end(3), "No group 3");
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class,
                () -> serializableMatchResult.end(3),
                "No group 3");
    }

    @Test
    void testGroupByIndex() {
        assertEquals("AAAA-BBBB-CCCC-DDDD", serializableMatchResult.group());
        assertEquals(matchResult.group(), serializableMatchResult.group());
        assertEquals("AAAA-BBBB-CCCC-DDDD", serializableMatchResult.group(0));
        assertEquals(matchResult.group(0), serializableMatchResult.group(0));
        assertEquals("BBBB", serializableMatchResult.group(1));
        assertEquals(matchResult.group(1), serializableMatchResult.group(1));
        assertEquals("CCCC", serializableMatchResult.group(2));
        assertEquals(matchResult.group(2), serializableMatchResult.group(2));
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class, () -> matchResult.group(3), "No group 3");
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class,
                () -> serializableMatchResult.group(3),
                "No group 3");
    }

    @Test
    void testGroupByName() {
        assertEquals("BBBB", serializableMatchResult.group("blockB"));
        assertEquals(matchResult.group("blockB"), serializableMatchResult.group("blockB"));
        assertEquals("CCCC", serializableMatchResult.group("blockC"));
        assertEquals(matchResult.group("blockC"), serializableMatchResult.group("blockC"));
        assertThrowsWithMessage(
                IllegalArgumentException.class,
                () -> matchResult.group("blockD"),
                "No group with name <blockD>");
        assertThrowsWithMessage(
                IllegalArgumentException.class,
                () -> serializableMatchResult.group("blockD"),
                "No group with name <blockD>");
    }

    @Test
    void testNamedGroups() {
        assertEquals(
                ofEntries(entry("blockB", 1), entry("blockC", 2)),
                serializableMatchResult.namedGroups());
        assertEquals(matchResult.namedGroups(), serializableMatchResult.namedGroups());
    }

    @Test
    void testNamedGroupValues() {
        assertEquals(
                ofEntries(entry("blockB", "BBBB"), entry("blockC", "CCCC")),
                serializableMatchResult.namedGroupValues());
        final var firstMatchResultNamedGroupValues =
                matchResult.namedGroups().entrySet().stream()
                        .collect(
                                toMap(Entry::getKey, entry -> matchResult.group(entry.getValue())));
        assertEquals(firstMatchResultNamedGroupValues, serializableMatchResult.namedGroupValues());
    }
}
