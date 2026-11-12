package ch.uetlib.job.model;

import static org.junit.jupiter.api.Assertions.*;

import ch.uetlib.job.model.regex.SerializableMatchResult;
import ch.uetlib.job.model.triggers.FileChangeEvent;
import ch.uetlib.job.model.triggers.TriggerType;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.regex.Pattern;

class TriggerContextTest {

    @Test
    void aRetryTriggerContextReportsTheRetryType() {
        final TriggerContext context = new RetryTriggerContext(0, 3, 1);

        assertEquals(TriggerType.RETRY, context.type());
    }

    @Test
    void aFileTriggerContextReportsTheFileType() {
        final var matcher = Pattern.compile("a").matcher("a");
        assertTrue(matcher.matches());
        final TriggerContext context =
                new FileTriggerContext(
                        Path.of("/tmp"),
                        FileChangeEvent.ENTRY_CREATED,
                        "a",
                        SerializableMatchResult.fromMatcher(matcher));

        assertEquals(TriggerType.FILE, context.type());
    }
}
