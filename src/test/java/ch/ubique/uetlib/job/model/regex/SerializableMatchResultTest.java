package ch.ubique.uetlib.job.model.regex;

import static ch.ubique.uetlib.test.assertion.AssertionUtils.assertThrowsWithMessage;
import static java.util.Map.*;
import static java.util.stream.Collectors.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

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

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Asserts that the SerializableMatchResult behaves identically to the JDK MatchResult for every
     * method in the MatchResult interface.
     */
    private static void assertMatchesJdk(
            final MatchResult expected, final SerializableMatchResult actual) {
        assertEquals(expected.hasMatch(), actual.hasMatch());
        assertEquals(expected.groupCount(), actual.groupCount());

        if (!expected.hasMatch()) {
            return;
        }

        // start/end for overall match
        assertEquals(expected.start(), actual.start());
        assertEquals(expected.end(), actual.end());

        // group()/group(0)
        assertEquals(expected.group(), actual.group());
        assertEquals(expected.group(0), actual.group(0));

        // Per-group: start, end, group value
        for (int g = 0; g <= expected.groupCount(); g++) {
            assertEquals(expected.start(g), actual.start(g), "start(" + g + ")");
            assertEquals(expected.end(g), actual.end(g), "end(" + g + ")");
            assertEquals(expected.group(g), actual.group(g), "group(" + g + ")");
        }

        // Out-of-bounds group
        final int oob = expected.groupCount() + 1;
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class, () -> actual.start(oob), "No group " + oob);
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class, () -> actual.end(oob), "No group " + oob);
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class, () -> actual.group(oob), "No group " + oob);

        // Negative group
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class, () -> actual.start(-1), "No group -1");
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class, () -> actual.end(-1), "No group -1");
        assertThrowsWithMessage(
                IndexOutOfBoundsException.class, () -> actual.group(-1), "No group -1");

        // Named groups map
        assertEquals(expected.namedGroups(), actual.namedGroups());

        // Named group lookup (if any names exist)
        for (final var entry : expected.namedGroups().entrySet()) {
            assertEquals(
                    expected.group(entry.getKey()),
                    actual.group(entry.getKey()),
                    "group(\"" + entry.getKey() + "\")");
        }
    }

    private void assertJacksonRoundTrip(final SerializableMatchResult result) {
        final var serialized = jsonMapper.writeValueAsString(result);
        final var deserialized = jsonMapper.readValue(serialized, SerializableMatchResult.class);
        assertEquals(result, deserialized);
        // Also verify the deserialized result behaves the same
        assertEquals(result.hasMatch(), deserialized.hasMatch());
        assertEquals(result.groupCount(), deserialized.groupCount());
        if (result.hasMatch()) {
            assertEquals(result.group(), deserialized.group());
            for (int g = 0; g <= result.groupCount(); g++) {
                assertEquals(result.group(g), deserialized.group(g), "group(" + g + ")");
                assertEquals(result.start(g), deserialized.start(g), "start(" + g + ")");
                assertEquals(result.end(g), deserialized.end(g), "end(" + g + ")");
            }
        }
    }

    // ── Original test data (named groups, full match) ───────────────────

    @Test
    void testSerializableMatchResultJackson() {
        assertJacksonRoundTrip(TEST_SERIALIZABLE_MATCH_RESULT);
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
        assertEquals(
                TEST_MATCH_RESULT.group("blockB"), TEST_SERIALIZABLE_MATCH_RESULT.group("blockB"));
        assertEquals("CCCC", TEST_SERIALIZABLE_MATCH_RESULT.group("blockC"));
        assertEquals(
                TEST_MATCH_RESULT.group("blockC"), TEST_SERIALIZABLE_MATCH_RESULT.group("blockC"));
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
                                toMap(
                                        Entry::getKey,
                                        entry -> TEST_MATCH_RESULT.group(entry.getValue())));
        assertEquals(
                firstMatchResultNamedGroupValues,
                TEST_SERIALIZABLE_MATCH_RESULT.namedGroupValues());
    }

    // ── Non-capturing groups (zero capturing groups) ────────────────────

    @Test
    void testRegexWithNonCapturingGroups() {
        final var pattern =
                Pattern.compile(
                        "(?:(?:Kamerabilder)?(?:XXXX|VRKJ)\\d{2}\\.|roundshot_\\d{4}_)\\d{6}\\.jpg");
        final var matcher = pattern.matcher("VRKJ01.123456.jpg");
        assertTrue(matcher.matches());
        final var result = SerializableMatchResult.fromMatcher(matcher);
        assertMatchesJdk(matcher.toMatchResult(), result);
        assertEquals(0, result.groupCount());
        assertEquals("VRKJ01.123456.jpg", result.group());
        assertJacksonRoundTrip(result);
    }

    // ── Optional / non-participating capturing groups ───────────────────

    @Test
    void testRegexWithNonParticipatingCapturingGroups() {
        final var pattern =
                Pattern.compile(
                        "(((Kamerabilder)?(XXXX|VRKJ)\\d{2}\\.)|(roundshot_\\d{4}_))\\d{6}\\.jpg");
        final var matcher = pattern.matcher("VRKJ01.123456.jpg");
        assertTrue(matcher.matches());
        final var result = SerializableMatchResult.fromMatcher(matcher);
        assertMatchesJdk(matcher.toMatchResult(), result);
        assertEquals(5, result.groupCount());
        assertEquals("VRKJ01.123456.jpg", result.group());
        // Group 1: outer alternation -> "VRKJ01."
        assertEquals("VRKJ01.", result.group(1));
        // Group 2: inner branch -> "VRKJ01."
        assertEquals("VRKJ01.", result.group(2));
        // Group 3: (Kamerabilder)? -> didn't participate
        assertNull(result.group(3));
        assertEquals(-1, result.start(3));
        assertEquals(-1, result.end(3));
        // Group 4: (XXXX|VRKJ) -> "VRKJ"
        assertEquals("VRKJ", result.group(4));
        // Group 5: (roundshot_\d{4}_) -> didn't participate (other branch)
        assertNull(result.group(5));
        assertEquals(-1, result.start(5));
        assertEquals(-1, result.end(5));
        assertJacksonRoundTrip(result);
    }

    @Test
    void testRegexWithNonParticipatingGroupsAlternateBranch() {
        // Same regex, but now matching the roundshot branch
        final var pattern =
                Pattern.compile(
                        "(((Kamerabilder)?(XXXX|VRKJ)\\d{2}\\.)|(roundshot_\\d{4}_))\\d{6}\\.jpg");
        final var matcher = pattern.matcher("roundshot_2024_123456.jpg");
        assertTrue(matcher.matches());
        final var result = SerializableMatchResult.fromMatcher(matcher);
        assertMatchesJdk(matcher.toMatchResult(), result);
        // Group 1: outer alternation -> "roundshot_2024_"
        assertEquals("roundshot_2024_", result.group(1));
        // Groups 2, 3, 4: first branch didn't participate
        assertNull(result.group(2));
        assertNull(result.group(3));
        assertNull(result.group(4));
        // Group 5: roundshot branch -> "roundshot_2024_"
        assertEquals("roundshot_2024_", result.group(5));
        assertJacksonRoundTrip(result);
    }

    @Test
    void testRegexWithAllOptionalGroupsParticipating() {
        // Same regex, but now with Kamerabilder present
        final var pattern =
                Pattern.compile(
                        "(((Kamerabilder)?(XXXX|VRKJ)\\d{2}\\.)|(roundshot_\\d{4}_))\\d{6}\\.jpg");
        final var matcher = pattern.matcher("KamerabilderVRKJ01.123456.jpg");
        assertTrue(matcher.matches());
        final var result = SerializableMatchResult.fromMatcher(matcher);
        assertMatchesJdk(matcher.toMatchResult(), result);
        // Now group 3 participates
        assertEquals("Kamerabilder", result.group(3));
        assertEquals("VRKJ", result.group(4));
        assertNull(result.group(5));
        assertJacksonRoundTrip(result);
    }

    // ── No match ────────────────────────────────────────────────────────

    @Test
    void testNoMatch() {
        final var pattern = Pattern.compile("xyz");
        final var matcher = pattern.matcher("abc");
        assertFalse(matcher.matches());
        final var result = SerializableMatchResult.fromMatcher(matcher);
        assertFalse(result.hasMatch());
        assertThrows(IllegalStateException.class, result::start);
        assertThrows(IllegalStateException.class, result::end);
        assertThrows(IllegalStateException.class, result::group);
        assertThrows(IllegalStateException.class, () -> result.group(0));
        assertThrows(IllegalStateException.class, () -> result.start(0));
        assertThrows(IllegalStateException.class, () -> result.end(0));
        // groupCount should still work even without a match
        assertEquals(0, result.groupCount());
        assertJacksonRoundTrip(result);
    }

    // ── Partial match (find, not at start) ──────────────────────────────

    @Test
    void testPartialMatchWithFind() {
        final var pattern = Pattern.compile("(?<word>\\w+)");
        final var matcher = pattern.matcher("  hello  world  ");
        assertTrue(matcher.find()); // first match: "hello" at index 2
        final var result = SerializableMatchResult.fromMatcher(matcher);
        assertMatchesJdk(matcher.toMatchResult(), result);
        assertEquals(2, result.start());
        assertEquals(7, result.end());
        assertEquals("hello", result.group());
        assertEquals("hello", result.group("word"));
        assertEquals(ofEntries(entry("word", "hello")), result.namedGroupValues());
        assertJacksonRoundTrip(result);
    }

    // ── Nested groups ───────────────────────────────────────────────────

    @Test
    void testNestedGroups() {
        // Pattern with deeply nested groups
        final var pattern = Pattern.compile("((a(b))(c))");
        final var matcher = pattern.matcher("abc");
        assertTrue(matcher.matches());
        final var result = SerializableMatchResult.fromMatcher(matcher);
        assertMatchesJdk(matcher.toMatchResult(), result);
        assertEquals(4, result.groupCount());
        assertEquals("abc", result.group(0)); // full match
        assertEquals("abc", result.group(1)); // ((a(b))(c))
        assertEquals("ab", result.group(2)); // (a(b))
        assertEquals("b", result.group(3)); // (b)
        assertEquals("c", result.group(4)); // (c)
        assertJacksonRoundTrip(result);
    }

    // ── Lookbehind with capturing group ─────────────────────────────────

    @Test
    void testLookbehindWithCapturingGroup() {
        // Lookbehind with a capturing group — the group captures text
        // before the overall match start
        final var pattern = Pattern.compile("(?<=(\\w{3}))\\d+");
        final var matcher = pattern.matcher("abc123");
        assertTrue(matcher.find());
        final var jdkResult = matcher.toMatchResult();
        final var result = SerializableMatchResult.fromMatcher(matcher);

        // Overall match is "123" starting at index 3
        assertEquals(3, result.start());
        assertEquals(6, result.end());
        assertEquals("123", result.group());

        // Group 1 captured "abc" — indices before the overall match start
        assertEquals(jdkResult.group(1), result.group(1));
        assertEquals(jdkResult.start(1), result.start(1));
        assertEquals(jdkResult.end(1), result.end(1));

        assertMatchesJdk(jdkResult, result);
        assertJacksonRoundTrip(result);
    }

    // ── Quantified group (only last iteration captured) ─────────────────

    @Test
    void testQuantifiedGroup() {
        // When a group is quantified, only the last iteration is captured
        final var pattern = Pattern.compile("(\\w)+");
        final var matcher = pattern.matcher("abcd");
        assertTrue(matcher.matches());
        final var result = SerializableMatchResult.fromMatcher(matcher);
        assertMatchesJdk(matcher.toMatchResult(), result);
        assertEquals("abcd", result.group(0));
        assertEquals("d", result.group(1)); // only last char captured
        assertJacksonRoundTrip(result);
    }

    // ── Empty group match ───────────────────────────────────────────────

    @Test
    void testEmptyGroupMatch() {
        // A group that matches the empty string (not the same as non-participating)
        final var pattern = Pattern.compile("(a?)b");
        final var matcher = pattern.matcher("b");
        assertTrue(matcher.matches());
        final var result = SerializableMatchResult.fromMatcher(matcher);
        assertMatchesJdk(matcher.toMatchResult(), result);
        // Group 1 participated but matched empty string
        assertEquals("", result.group(1));
        assertNotNull(result.group(1)); // empty, not null!
        assertEquals(0, result.start(1));
        assertEquals(0, result.end(1));
        assertJacksonRoundTrip(result);
    }

    // ── Multiple named groups with some not participating ───────────────

    @Test
    void testNamedGroupsWithNonParticipating() {
        final var pattern = Pattern.compile("(?<a>x)|(?<b>y)");
        final var matcher = pattern.matcher("x");
        assertTrue(matcher.matches());
        final var result = SerializableMatchResult.fromMatcher(matcher);
        assertMatchesJdk(matcher.toMatchResult(), result);
        assertEquals("x", result.group("a"));
        assertNull(result.group("b"));
        assertEquals(-1, result.start(2));
        assertEquals(-1, result.end(2));
        assertJacksonRoundTrip(result);
    }

    // ── groups() list accessor ──────────────────────────────────────────

    @Test
    void testGroupsList() {
        final var pattern = Pattern.compile("(a)(b)?(c)");
        final var matcher = pattern.matcher("ac");
        assertTrue(matcher.matches());
        final var result = SerializableMatchResult.fromMatcher(matcher);
        assertMatchesJdk(matcher.toMatchResult(), result);
        final var groups = result.groups();
        // groups list includes group 0 (full match) through groupCount
        assertEquals(4, groups.size()); // group 0..3
        assertEquals("ac", groups.get(0));
        assertEquals("a", groups.get(1));
        assertNull(groups.get(2)); // (b)? didn't participate
        assertEquals("c", groups.get(3));
    }

    @Test
    void testGroupsListNoMatch() {
        final var pattern = Pattern.compile("xyz");
        final var matcher = pattern.matcher("abc");
        assertFalse(matcher.matches());
        final var result = SerializableMatchResult.fromMatcher(matcher);
        assertTrue(result.groups().isEmpty());
    }

    // ── Equality and hashCode ───────────────────────────────────────────

    @Test
    void testEqualityOfEquivalentResults() {
        // Create two results from the same pattern/input
        final var pattern = Pattern.compile("(\\w+)-(\\w+)");
        final var matcher1 = pattern.matcher("hello-world");
        assertTrue(matcher1.matches());
        final var result1 = SerializableMatchResult.fromMatcher(matcher1);

        final var matcher2 = pattern.matcher("hello-world");
        assertTrue(matcher2.matches());
        final var result2 = SerializableMatchResult.fromMatcher(matcher2);

        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
    }

    @Test
    void testInequalityOfDifferentResults() {
        final var pattern = Pattern.compile("(\\w+)-(\\w+)");
        final var matcher1 = pattern.matcher("hello-world");
        assertTrue(matcher1.matches());
        final var result1 = SerializableMatchResult.fromMatcher(matcher1);

        final var matcher2 = pattern.matcher("foo-bar");
        assertTrue(matcher2.matches());
        final var result2 = SerializableMatchResult.fromMatcher(matcher2);

        assertNotEquals(result1, result2);
    }
}
