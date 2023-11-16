package ch.ubique.backend.job.service.triggers;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.job.model.triggers.FileChangeEvent;
import ch.ubique.backend.job.model.triggers.FileTrigger;

import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.regex.Pattern;

class FileTriggerServiceTest {

    @Test
    void test() throws IOException, InterruptedException {
        final var triggered = new HashBag<FileTrigger>();
        final var tempDir = Files.createTempDirectory(null);

        final var anyFileCreated =
                new FileTrigger(
                        tempDir.toString(), Pattern.compile(".*"), FileChangeEvent.ENTRY_CREATED);
        final var testFileDeleted =
                new FileTrigger(
                        tempDir.toString(),
                        Pattern.compile("test.txt"),
                        FileChangeEvent.ENTRY_DELETED);

        try (final var fileTriggerService = new FileTriggerService()) {

            fileTriggerService.addTrigger(
                    0, anyFileCreated, jobRunArguments -> triggered.add(anyFileCreated));
            fileTriggerService.addTrigger(
                    1, testFileDeleted, jobRunArguments -> triggered.add(testFileDeleted));

            final var test = Files.createFile(tempDir.resolve("test.txt"));
            final var foo = Files.createFile(tempDir.resolve("foo.txt"));
            final var bar = Files.createFile(tempDir.resolve("bar.txt"));

            Thread.sleep(Duration.ofSeconds(3).toMillis());

            Files.delete(test);
            Files.delete(foo);
            Files.delete(bar);
        }

        assertEquals(3, triggered.getCount(anyFileCreated));
        assertEquals(1, triggered.getCount(testFileDeleted));

        FileUtils.deleteDirectory(tempDir.toFile());
    }
}
