package ch.uetlib.job.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JobApplicationArgsParserTest {

    private final JobApplicationArgsParser parser = new JobApplicationArgsParser();

    @Test
    void anUnrecognizedArgIsAParseFailure() {
        final var result =
                parser.parse(
                        new String[] {
                            "--spring.profiles.active=postgres,no-security", "--server.port=8080"
                        });

        assertTrue(result.isEmpty(), "an unrecognized arg is a genuine usage error here");
    }

    @Test
    void noArgsStartsNormally() {
        final var result = parser.parse(new String[] {});

        assertTrue(result.isPresent());
        assertNull(result.get().jobToStart());
    }

    @Test
    void jobFlagMixedWithAnUnrecognizedArgIsAParseFailure() {
        final var result =
                parser.parse(new String[] {"--job", "ch.uetlib.job.TestJob", "--server.port=8080"});

        assertTrue(result.isEmpty(), "--job does not make other, unrecognized args acceptable");
    }

    @Test
    void helpFlagPrintsUsageAndPreventsStartup() {
        final var result = parser.parse(new String[] {"--help"});

        assertTrue(result.isEmpty());
    }

    @Test
    void jobFlagWithoutARequiredValueIsAGenuineUsageError() {
        final var result = parser.parse(new String[] {"--job"});

        assertTrue(result.isEmpty(), "a malformed --job invocation is a real parse error");
    }

    @Test
    void additionalDataFlagsAreParsedCorrectly() {
        final var result =
                parser.parse(
                        new String[] {
                            "--job",
                            "ch.uetlib.job.TestJob",
                            "--additionalData",
                            "{\"foo\":\"bar\"}",
                            "--additionalDataClass",
                            "ch.uetlib.job.SomeAdditionalData"
                        });

        final var jobToStart = result.orElseThrow().jobToStart();
        assertNotNull(jobToStart);
        assertEquals("{\"foo\":\"bar\"}", jobToStart.additionalDataString());
        assertEquals("ch.uetlib.job.SomeAdditionalData", jobToStart.additionalDataClassName());
    }

    @Test
    void forceFlagAndRunIdAreParsedCorrectly() {
        final var result =
                parser.parse(
                        new String[] {"--job", "ch.uetlib.job.TestJob", "--run", "42", "--force"});

        final var jobToStart = result.orElseThrow().jobToStart();
        assertNotNull(jobToStart);
        assertEquals(42, jobToStart.jobRunId());
        assertTrue(jobToStart.forceRun());
    }
}
