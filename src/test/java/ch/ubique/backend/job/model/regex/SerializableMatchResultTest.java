package ch.ubique.backend.job.model.regex;

import static ch.ubique.backend.test.assertion.AssertionUtils.assertThrowsWithMessage;

import static org.junit.jupiter.api.Assertions.*;

import static java.util.Map.*;
import static java.util.stream.Collectors.*;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SerializableMatchResultTest {

    private static final Pattern TEST_PATTERN =
            Pattern.compile("\\w+-(?<blockB>\\w+)-(?<blockC>\\w+)-\\w+");
    private static final Matcher TEST_MATCHER;
    private static final MatchResult TEST_MATCH_RESULT;
    private static final SerializableMatchResult TEST_SERIALIZABLE_MATCH_RESULT;

    static {
        TEST_MATCHER = TEST_PATTERN.matcher("AAAA-BBBB-CCCC-DDDD");
        //noinspection ResultOfMethodCallIgnored
        TEST_MATCHER.matches();
        TEST_MATCH_RESULT = TEST_MATCHER.toMatchResult();
        TEST_SERIALIZABLE_MATCH_RESULT = SerializableMatchResult.fromMatcher(TEST_MATCHER);
    }

    private final JsonMapper jsonMapper = JsonMapper.shared();

    @Test
    void testSerializableMatchResultJackson() {
        final var serialized = jsonMapper.writeValueAsString(TEST_SERIALIZABLE_MATCH_RESULT);
        final var deserialized = jsonMapper.readValue(serialized, SerializableMatchResult.class);
        assertEquals(TEST_SERIALIZABLE_MATCH_RESULT, deserialized);
    }

    @Test
    void testMatcherMatches() {
        assertTrue(TEST_MATCHER.matches());
    }

    @Test
    void testHasMatch() {
        assertTrue(TEST_SERIALIZABLE_MATCH_RESULT.hasMatch());
        assertEquals(TEST_MATCH_RESULT.hasMatch(), TEST_SERIALIZABLE_MATCH_RESULT.hasMatch());
    }

    @Test
    void testGroupCount() {
        assertEquals(2, TEST_SERIALIZABLE_MATCH_RESULT.groupCount());
        assertEquals(TEST_MATCH_RESULT.groupCount(), TEST_SERIALIZABLE_MATCH_RESULT.groupCount());
    }

    @Test
    void testStart() {
        assertEquals(0, TEST_SERIALIZABLE_MATCH_RESULT.start());
        assertEquals(TEST_MATCH_RESULT.start(), TEST_SERIALIZABLE_MATCH_RESULT.start());
        assertEquals(0, TEST_SERIALIZABLE_MATCH_RESULT.start(0));
        assertEquals(TEST_MATCH_RESULT.start(0), TEST_SERIALIZABLE_MATCH_RESULT.start(0));
        assertEquals(5, TEST_SERIALIZABLE_MATCH_RESULT.start(1));
        assertEquals(TEST_MATCH_RESULT.start(1), TEST_SERIALIZABLE_MATCH_RESULT.start(1));
        assertEquals(10, TEST_SERIALIZABLE_MATCH_RESULT.start(2));
        assertEquals(TEST_MATCH_RESULT.start(2), TEST_SERIALIZABLE_MATCH_RESULT.start(2));
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class, () -> TEST_MATCH_RESULT.start(3), "No group 3");
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class,
                () -> TEST_SERIALIZABLE_MATCH_RESULT.start(3),
                "No group 3");
    }

    @Test
    void testEnd() {
        assertEquals(19, TEST_SERIALIZABLE_MATCH_RESULT.end());
        assertEquals(TEST_MATCH_RESULT.end(), TEST_SERIALIZABLE_MATCH_RESULT.end());
        assertEquals(19, TEST_SERIALIZABLE_MATCH_RESULT.end(0));
        assertEquals(TEST_MATCH_RESULT.end(0), TEST_SERIALIZABLE_MATCH_RESULT.end(0));
        assertEquals(9, TEST_SERIALIZABLE_MATCH_RESULT.end(1));
        assertEquals(TEST_MATCH_RESULT.end(1), TEST_SERIALIZABLE_MATCH_RESULT.end(1));
        assertEquals(14, TEST_SERIALIZABLE_MATCH_RESULT.end(2));
        assertEquals(TEST_MATCH_RESULT.end(2), TEST_SERIALIZABLE_MATCH_RESULT.end(2));
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class, () -> TEST_MATCH_RESULT.end(3), "No group 3");
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class,
                () -> TEST_SERIALIZABLE_MATCH_RESULT.end(3),
                "No group 3");
    }

    @Test
    void testGroupByIndex() {
        assertEquals("AAAA-BBBB-CCCC-DDDD", TEST_SERIALIZABLE_MATCH_RESULT.group());
        assertEquals(TEST_MATCH_RESULT.group(), TEST_SERIALIZABLE_MATCH_RESULT.group());
        assertEquals("AAAA-BBBB-CCCC-DDDD", TEST_SERIALIZABLE_MATCH_RESULT.group(0));
        assertEquals(TEST_MATCH_RESULT.group(0), TEST_SERIALIZABLE_MATCH_RESULT.group(0));
        assertEquals("BBBB", TEST_SERIALIZABLE_MATCH_RESULT.group(1));
        assertEquals(TEST_MATCH_RESULT.group(1), TEST_SERIALIZABLE_MATCH_RESULT.group(1));
        assertEquals("CCCC", TEST_SERIALIZABLE_MATCH_RESULT.group(2));
        assertEquals(TEST_MATCH_RESULT.group(2), TEST_SERIALIZABLE_MATCH_RESULT.group(2));
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class, () -> TEST_MATCH_RESULT.group(3), "No group 3");
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class,
                () -> TEST_SERIALIZABLE_MATCH_RESULT.group(3),
                "No group 3");
    }

    @Test
    void testGroupByName() {
        assertEquals("BBBB", TEST_SERIALIZABLE_MATCH_RESULT.group("blockB"));
        assertEquals(TEST_MATCH_RESULT.group("blockB"), TEST_SERIALIZABLE_MATCH_RESULT.group("blockB"));
        assertEquals("CCCC", TEST_SERIALIZABLE_MATCH_RESULT.group("blockC"));
        assertEquals(TEST_MATCH_RESULT.group("blockC"), TEST_SERIALIZABLE_MATCH_RESULT.group("blockC"));
        assertThrowsWithMessage(
                IllegalArgumentException.class,
                () -> TEST_MATCH_RESULT.group("blockD"),
                "No group with name <blockD>");
        assertThrowsWithMessage(
                IllegalArgumentException.class,
                () -> TEST_SERIALIZABLE_MATCH_RESULT.group("blockD"),
                "No group with name <blockD>");
    }

    @Test
    void testNamedGroups() {
        assertEquals(
                ofEntries(entry("blockB", 1), entry("blockC", 2)),
                TEST_SERIALIZABLE_MATCH_RESULT.namedGroups());
        assertEquals(TEST_MATCH_RESULT.namedGroups(), TEST_SERIALIZABLE_MATCH_RESULT.namedGroups());
    }

    @Test
    void testNamedGroupValues() {
        assertEquals(
                ofEntries(entry("blockB", "BBBB"), entry("blockC", "CCCC")),
                TEST_SERIALIZABLE_MATCH_RESULT.namedGroupValues());
        final var firstMatchResultNamedGroupValues =
                TEST_MATCH_RESULT.namedGroups().entrySet().stream()
                        .collect(
                                toMap(Entry::getKey, entry -> TEST_MATCH_RESULT.group(entry.getValue())));
        assertEquals(firstMatchResultNamedGroupValues, TEST_SERIALIZABLE_MATCH_RESULT.namedGroupValues());
    }
}
